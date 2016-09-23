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

package io.crate.sql.parser;

import org.antlr.runtime.CharStream;

class CaseInsensitiveStream implements CharStream {

    private CharStream stream;

    CaseInsensitiveStream(CharStream stream) {
        this.stream = stream;
    }

    /**
     * @return the LA value without case transformation
     */
    public int rawLA(int i) {
        return stream.LA(i);
    }

    @Override
    public String substring(int start, int stop) {
        return stream.substring(start, stop);
    }

    @Override
    public int LT(int i) {
        return LA(i);
    }

    @Override
    public int getLine() {
        return stream.getLine();
    }

    @Override
    public void setLine(int line) {
        stream.setLine(line);
    }

    @Override
    public void consume() {
        stream.consume();
    }

    @Override
    public void setCharPositionInLine(int pos) {
        stream.setCharPositionInLine(pos);
    }

    @Override
    public int LA(int i) {
        int result = stream.LT(i);

        switch (result) {
            case 0:
            case CharStream.EOF:
                return result;
            default:
                return Character.toUpperCase(result);
        }
    }

    @Override
    public int getCharPositionInLine() {
        return stream.getCharPositionInLine();
    }

    @Override
    public int mark() {
        return stream.mark();
    }

    @Override
    public int index() {
        return stream.index();
    }

    @Override
    public void rewind(int marker) {
        stream.rewind(marker);
    }

    @Override
    public void rewind() {
        stream.rewind();
    }

    @Override
    public void release(int marker) {
        stream.release(marker);
    }

    @Override
    public void seek(int index) {
        stream.seek(index);
    }

    @Override
    public int size() {
        return stream.size();
    }

    @Override
    public String getSourceName() {
        return stream.getSourceName();
    }
}
