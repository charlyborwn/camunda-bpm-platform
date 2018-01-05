/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.camunda.bpm.engine.impl.bpmn.parser;

import java.util.List;

import org.camunda.bpm.engine.impl.el.Expression;

public class FailedJobRetryConfiguration {

  protected int retries;
  protected List<String> retryIntervals;
  protected Expression expression;

  public FailedJobRetryConfiguration(Expression expression) {
    this.expression = expression;
  }

  public FailedJobRetryConfiguration(int retries, List<String> retryIntervals) {
    this.retries = retries;
    this.retryIntervals = retryIntervals;
  }

  public int getRetries() {
    return retries;
  }

  public List<String> getRetryIntervals() {
    return retryIntervals;
  }

  public Expression getExpression() {
    return expression;
  }
}