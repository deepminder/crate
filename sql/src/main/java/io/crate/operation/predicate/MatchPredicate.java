/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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

package io.crate.operation.predicate;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import io.crate.analyze.symbol.Function;
import io.crate.analyze.symbol.Symbol;
import io.crate.analyze.symbol.Symbols;
import io.crate.metadata.*;
import io.crate.types.DataType;
import io.crate.types.DataTypes;
import org.elasticsearch.ElasticsearchParseException;
import org.elasticsearch.common.ParseFieldMatcher;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The match predicate is only used to generate lucene queries from.
 */
public class MatchPredicate implements FunctionImplementation<Function> {

    public static final Set<DataType> SUPPORTED_TYPES = ImmutableSet.<DataType>of(DataTypes.STRING, DataTypes.GEO_SHAPE);

    private static final String DEFAULT_MATCH_TYPE_STRING = MultiMatchQueryBuilder.Type.BEST_FIELDS.toString().toLowerCase(Locale.ENGLISH);
    private static final Map<DataType, String> DATA_TYPE_TO_DEFAULT_MATCH_TYPE = ImmutableMap.<DataType, String>of(
        DataTypes.STRING, DEFAULT_MATCH_TYPE_STRING,
        DataTypes.GEO_SHAPE, "intersects"
    );
    private static final Set<String> SUPPORTED_GEO_MATCH_TYPES = ImmutableSet.of("intersects", "disjoint", "within");

    public static final String NAME = "match";
    private final FunctionInfo info;

    private static String defaultMatchType(DataType dataType) {
        String matchType = DATA_TYPE_TO_DEFAULT_MATCH_TYPE.get(dataType);
        if (matchType == null) {
            throw new IllegalArgumentException("No default matchType found for dataType: " + dataType);
        }
        return matchType;
    }


    public static String getMatchType(@Nullable String matchType, DataType columnType) {
        if (matchType == null) {
            return defaultMatchType(columnType);
        }
        if (columnType.equals(DataTypes.STRING)) {
            try {
                MultiMatchQueryBuilder.Type.parse(matchType, ParseFieldMatcher.STRICT);
                return matchType;
            } catch (ElasticsearchParseException e) {
                throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                    "invalid MATCH type '%s' for type '%s'", matchType, columnType), e);
            }
        } else if (columnType.equals(DataTypes.GEO_SHAPE)) {
            if (!SUPPORTED_GEO_MATCH_TYPES.contains(matchType)) {
                throw new IllegalArgumentException(String.format(Locale.ENGLISH,
                    "invalid MATCH type '%s' for type '%s', valid types are: [%s]",
                    matchType, columnType, Joiner.on(",").join(SUPPORTED_GEO_MATCH_TYPES)));
            }
            return matchType;
        }
        throw new IllegalArgumentException("No match type for dataType: " + columnType);
    }

    private static FunctionInfo createInfo(List<DataType> argumentTypes) {
        return new FunctionInfo(new FunctionIdent(NAME, argumentTypes), DataTypes.BOOLEAN);
    }

    public static Function createSymbol(List<Symbol> args) {
        return new Function(createInfo(Symbols.extractTypes(args)), args);
    }

    /**
     * the match predicate is registered as a regular function
     * though it is called differently by the parser. We mangle
     * the arguments and options for the match predicate into
     * function arguments.
     * <p>
     * 1. list of column idents and boost values - object mapping column name to boost value (Double) (values nullable)
     * 2. query string - string
     * 3. match_type - string (nullable)
     * 4. match_type options - object mapping option name to value (Object) (nullable)
     */
    public static void register(PredicateModule module) {
        module.register(NAME, new DynamicFunctionResolver() {
            @Override
            public FunctionImplementation<Function> getForTypes(List<DataType> dataTypes) throws IllegalArgumentException {
                return new MatchPredicate(createInfo(dataTypes));
            }
        });
    }

    public MatchPredicate(FunctionInfo info) {
        this.info = info;
    }

    @Override
    public FunctionInfo info() {
        return info;
    }

    @Override
    public Symbol normalizeSymbol(Function function, StmtCtx stmtCtx) {
        return function;
    }
}
