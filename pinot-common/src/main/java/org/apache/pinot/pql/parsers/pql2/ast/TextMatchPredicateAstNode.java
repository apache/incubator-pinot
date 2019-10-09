/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.pinot.pql.parsers.pql2.ast;

import com.google.common.base.Preconditions;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.pinot.common.request.Expression;
import org.apache.pinot.common.request.FilterOperator;
import org.apache.pinot.common.utils.StringUtil;
import org.apache.pinot.common.utils.request.FilterQueryTree;
import org.apache.pinot.common.utils.request.HavingQueryTree;
import org.apache.pinot.common.utils.request.RequestUtils;
import org.apache.pinot.pql.parsers.Pql2CompilationException;


public class TextMatchPredicateAstNode extends PredicateAstNode {

  private static final String SEPERATOR = "\t\t";

  @Override
  public void addChild(AstNode childNode) {
    if (childNode instanceof IdentifierAstNode) {
      if (_identifier == null) {
        IdentifierAstNode node = (IdentifierAstNode) childNode;
        _identifier = node.getName();
      } else {
        throw new Pql2CompilationException("REGEXP_LIKE predicate has more than one identifier.");
      }
    } else if (childNode instanceof FunctionCallAstNode) {
      throw new Pql2CompilationException("TEXT_MATCH is not supported with function");
    } else {
      super.addChild(childNode);
    }
  }

  @Override
  public FilterQueryTree buildFilterQueryTree() {
    if (_identifier == null) {
      throw new Pql2CompilationException("TEXT_MATCH predicate has no identifier");
    }

    List<? extends AstNode> children = getChildren();
    Preconditions.checkState(children != null && children.size() == 1);
    AstNode child = children.get(0);
    Preconditions.checkState(child instanceof StringLiteralAstNode);
    String expr = ((StringLiteralAstNode)child).getValueAsString();

    FilterOperator filterOperator = FilterOperator.TEXT_MATCH;
    List<String> value = Collections.singletonList(expr);
    return new FilterQueryTree(_identifier, value, filterOperator, null);
  }

  @Override
  public Expression buildFilterExpression() {
    return null;
  }

  @Override
  public HavingQueryTree buildHavingQueryTree() {
    throw new UnsupportedOperationException("TEXT_MATCH is not supported with HAVING");
  }
}
