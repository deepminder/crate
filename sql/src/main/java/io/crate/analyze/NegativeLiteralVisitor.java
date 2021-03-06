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
package io.crate.analyze;

import io.crate.analyze.symbol.Literal;
import io.crate.analyze.symbol.Symbol;
import io.crate.analyze.symbol.SymbolVisitor;
import io.crate.analyze.symbol.format.SymbolFormatter;
import io.crate.types.*;

public class NegativeLiteralVisitor extends SymbolVisitor<Void, Literal> {

    @Override
    public Literal visitLiteral(Literal symbol, Void context) {
        if (symbol.value() == null) {
            return symbol;
        }
        switch (symbol.valueType().id()) {
            case DoubleType.ID:
                return Literal.of(symbol.valueType(), (Double) symbol.value() * -1);
            case FloatType.ID:
                return Literal.of(symbol.valueType(), (Double) symbol.value() * -1);
            case ShortType.ID:
                return Literal.of(symbol.valueType(), (Short) symbol.value() * -1);
            case IntegerType.ID:
                return Literal.of(symbol.valueType(), (Integer) symbol.value() * -1);
            case LongType.ID:
                return Literal.of(symbol.valueType(), (Long) symbol.value() * -1);
            default:
                return symbol;
        }
    }

    @Override
    protected Literal visitSymbol(Symbol symbol, Void context) {
        throw new UnsupportedOperationException(SymbolFormatter.format("Cannot negate symbol %s", symbol));
    }
}
