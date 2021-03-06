/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.analyze;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import io.crate.analyze.symbol.DefaultTraversalSymbolVisitor;
import io.crate.analyze.symbol.Function;
import io.crate.analyze.symbol.RelationColumn;
import io.crate.analyze.symbol.Symbol;
import io.crate.metadata.StmtCtx;
import io.crate.operation.scalar.cast.CastFunctionResolver;
import io.crate.types.DataType;
import io.crate.types.DataTypes;

import javax.annotation.Nullable;
import java.util.*;

public class QuerySpec {

    private Optional<List<Symbol>> groupBy = Optional.absent();
    private Optional<OrderBy> orderBy = Optional.absent();
    private Optional<HavingClause> having = Optional.absent();
    private List<Symbol> outputs;
    private WhereClause where = WhereClause.MATCH_ALL;
    private Optional<Symbol> limit = Optional.absent();
    private Optional<Symbol> offset = Optional.absent();
    private boolean hasAggregates = false;

    public Optional<List<Symbol>> groupBy() {
        return groupBy;
    }

    public QuerySpec groupBy(@Nullable List<Symbol> groupBy) {
        assert groupBy == null || groupBy.size() > 0 : "groupBy must not be empty";
        this.groupBy = Optional.fromNullable(groupBy);
        return this;
    }

    public WhereClause where() {
        return where;
    }

    public QuerySpec where(@Nullable WhereClause where) {
        if (where == null) {
            this.where = WhereClause.MATCH_ALL;
        } else {
            this.where = where;
        }
        return this;
    }

    public Optional<Symbol> limit() {
        return limit;
    }

    public QuerySpec limit(Optional<Symbol> limit) {
        assert limit != null : "argument limit must not be null but absent";
        this.limit = limit;
        return this;
    }

    public Optional<Symbol> offset() {
        return offset;
    }

    public QuerySpec offset(Optional<Symbol> offset) {
        this.offset = offset;
        return this;
    }

    public Optional<HavingClause> having() {
        return having;
    }

    public QuerySpec having(@Nullable HavingClause having) {
        if (having == null || !having.hasQuery() && !having.noMatch()) {
            this.having = Optional.absent();
        } else {
            this.having = Optional.of(having);
        }
        return this;
    }

    public Optional<OrderBy> orderBy() {
        return orderBy;
    }

    public QuerySpec orderBy(@Nullable OrderBy orderBy) {
        this.orderBy = Optional.fromNullable(orderBy);
        return this;
    }

    public List<Symbol> outputs() {
        return outputs;
    }

    public QuerySpec outputs(List<Symbol> outputs) {
        this.outputs = outputs;
        return this;
    }

    public boolean hasAggregates() {
        return hasAggregates;
    }

    public QuerySpec hasAggregates(boolean hasAggregates) {
        this.hasAggregates = hasAggregates;
        return this;
    }

    public void normalize(EvaluatingNormalizer normalizer, StmtCtx context) {
        if (groupBy.isPresent()) {
            normalizer.normalizeInplace(groupBy.get(), context);
        }
        if (orderBy.isPresent()) {
            orderBy.get().normalize(normalizer, context);
        }
        if (outputs != null) {
            normalizer.normalizeInplace(outputs, context);
        }
        if (where != null && where != WhereClause.MATCH_ALL) {
            this.where(where.normalize(normalizer, context));
        }
        if (having.isPresent()) {
            Optional.of(having.get().normalize(normalizer, context));
        }
    }

    /**
     * Tries to cast the outputs to the given types. Types might contain Null values, which indicates to skip that
     * position. The iterator needs to return exactly the same number of types as there are outputs.
     *
     * @param types an iterable providing the types
     * @return -1 if all casts where successfully applied or the position of the failed cast
     */
    public int castOutputs(Iterator<DataType> types) {
        int i = 0;
        ListIterator<Symbol> outputsIt = outputs.listIterator();
        while (types.hasNext() && outputsIt.hasNext()) {
            DataType targetType = types.next();
            assert targetType != null : "targetType must not be null";

            Symbol output = outputsIt.next();
            DataType sourceType = output.valueType();
            if (!sourceType.equals(targetType)) {
                if (sourceType.isConvertableTo(targetType)) {
                    Function castFunction = new Function(
                        CastFunctionResolver.functionInfo(sourceType, targetType, false),
                        Arrays.asList(output));
                    if (groupBy().isPresent()) {
                        Collections.replaceAll(groupBy().get(), output, castFunction);
                    }
                    if (orderBy().isPresent()) {
                        Collections.replaceAll(orderBy().get().orderBySymbols(), output, castFunction);
                    }
                    outputsIt.set(castFunction);
                } else if (!targetType.equals(DataTypes.UNDEFINED)) {
                    return i;
                }
            }
            i++;
        }
        assert i == outputs.size();
        return -1;
    }

    /**
     * create a new QuerySpec which is a subset of this which contains only symbols which match the predicate
     */
    public QuerySpec subset(Predicate<? super Symbol> predicate, boolean traverseFunctions) {
        if (hasAggregates) {
            throw new UnsupportedOperationException("Cannot create a subset of a querySpec if it has aggregations");
        }

        QuerySpec newSpec = new QuerySpec()
            .limit(limit)
            .offset(offset);
        if (traverseFunctions) {
            newSpec.outputs(SubsetVisitor.filter(outputs, predicate));
        } else {
            newSpec.outputs(Lists.newArrayList(Iterables.filter(outputs, predicate)));
        }

        if (!where.hasQuery()) {
            newSpec.where(where);
        } else if (predicate.apply(where.query())) {
            newSpec.where(where);
        }

        if (orderBy.isPresent()) {
            newSpec.orderBy(orderBy.get().subset(predicate));
        }

        return newSpec;
    }

    private static class SubsetVisitor extends DefaultTraversalSymbolVisitor<SubsetVisitor.SubsetContext, Void> {

        public static class SubsetContext {
            Predicate<? super Symbol> predicate;
            List<Symbol> outputs = new ArrayList<>();
        }

        public static List<Symbol> filter(List<Symbol> outputs, Predicate<? super Symbol> predicate) {
            SubsetVisitor.SubsetContext ctx = new SubsetVisitor.SubsetContext();
            ctx.predicate = predicate;
            SubsetVisitor visitor = new SubsetVisitor();
            for (Symbol output : outputs) {
                visitor.process(output, ctx);
            }
            return ctx.outputs;
        }

        @Override
        public Void visitRelationColumn(RelationColumn relationColumn, SubsetContext context) {
            if (context.predicate.apply(relationColumn)) {
                context.outputs.add(relationColumn);
            }
            return null;
        }
    }


    public QuerySpec copyAndReplace(com.google.common.base.Function<? super Symbol, Symbol> replaceFunction) {
        if (groupBy.isPresent() || having.isPresent()) {
            throw new UnsupportedOperationException("Replacing group by or having symbols is not implemented");
        }

        QuerySpec newSpec = new QuerySpec()
            .limit(limit)
            .offset(offset)
            .outputs(Lists.transform(outputs, replaceFunction));
        if (!where.hasQuery()) {
            newSpec.where(where);
        } else {
            newSpec.where(new WhereClause(replaceFunction.apply(where.query()), where.docKeys().orNull(), where.partitions()));
        }
        if (orderBy.isPresent()) {
            newSpec.orderBy(orderBy.get().copyAndReplace(replaceFunction));
        }
        return newSpec;
    }

    public void replace(com.google.common.base.Function<? super Symbol, Symbol> replaceFunction) {
        ListIterator<Symbol> listIt = outputs.listIterator();
        while (listIt.hasNext()) {
            listIt.set(replaceFunction.apply(listIt.next()));
        }
        if (where.hasQuery()) {
            where = new WhereClause(replaceFunction.apply(where.query()), where.docKeys().orNull(), where.partitions());
        }
        if (orderBy.isPresent()) {
            orderBy.get().replace(replaceFunction);
        }
    }

    @Override
    public String toString() {
        return String.format(Locale.ENGLISH,
            "QS{ SELECT %s WHERE %s GROUP BY %s HAVING %s ORDER BY %s LIMIT %s OFFSET %s}",
            outputs, where, groupBy, having, orderBy, limit, offset);
    }
}
