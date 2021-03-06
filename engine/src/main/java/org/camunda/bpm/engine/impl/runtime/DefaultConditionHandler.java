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

package org.camunda.bpm.engine.impl.runtime;

import java.util.ArrayList;
import java.util.List;

import org.camunda.bpm.engine.ProcessEngineException;
import org.camunda.bpm.engine.impl.ProcessEngineLogger;
import org.camunda.bpm.engine.impl.bpmn.helper.BpmnProperties;
import org.camunda.bpm.engine.impl.bpmn.parser.ConditionalEventDefinition;
import org.camunda.bpm.engine.impl.bpmn.parser.EventSubscriptionDeclaration;
import org.camunda.bpm.engine.impl.cmd.CommandLogger;
import org.camunda.bpm.engine.impl.event.EventType;
import org.camunda.bpm.engine.impl.interceptor.CommandContext;
import org.camunda.bpm.engine.impl.javax.el.PropertyNotFoundException;
import org.camunda.bpm.engine.impl.persistence.deploy.cache.DeploymentCache;
import org.camunda.bpm.engine.impl.persistence.entity.EventSubscriptionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.EventSubscriptionManager;
import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;
import org.camunda.bpm.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.camunda.bpm.engine.impl.pvm.process.ActivityImpl;

public class DefaultConditionHandler implements ConditionHandler {

  private final static CommandLogger LOG = ProcessEngineLogger.CMD_LOGGER;

  @Override
  public List<ConditionHandlerResult> evaluateStartCondition(CommandContext commandContext, ConditionSet conditionSet) {
    if (conditionSet.getProcessDefinitionId() == null) {
      return evaluateConditionStartByEventSubscription(commandContext, conditionSet);
    } else {
      return evaluateConditionStartByProcessDefinitionId(commandContext, conditionSet, conditionSet.getProcessDefinitionId());
    }
  }

  protected List<ConditionHandlerResult> evaluateConditionStartByEventSubscription(CommandContext commandContext, ConditionSet conditionSet) {
    List<EventSubscriptionEntity> subscriptions = findConditionalStartEventSubscriptions(commandContext, conditionSet);
    if (subscriptions == null || subscriptions.isEmpty()) {
      throw new ProcessEngineException("No subscriptions were found during evaluation of the conditional start events.");
    }
    List<ConditionHandlerResult> results = new ArrayList<ConditionHandlerResult>();
    for (EventSubscriptionEntity subscription : subscriptions) {

      ProcessDefinitionEntity processDefinition = subscription.getProcessDefinition();
      if (!processDefinition.isSuspended()) {

        ActivityImpl activity = subscription.getActivity();

        if (evaluateCondition(conditionSet, activity)) {
          results.add(new ConditionHandlerResult(processDefinition, activity));
        }

      }
    }

    return results;
  }

  protected List<EventSubscriptionEntity> findConditionalStartEventSubscriptions(CommandContext commandContext, ConditionSet conditionSet) {
    EventSubscriptionManager eventSubscriptionManager = commandContext.getEventSubscriptionManager();

    if (conditionSet.isTenantIdSet) {
      List<EventSubscriptionEntity> subscriptions = eventSubscriptionManager.findConditionalStartEventSubscriptionByTenantId(conditionSet.getTenantId());
      if (subscriptions == null || subscriptions.isEmpty()) {
        return null;
      } else {
        return subscriptions;
      }
    } else {
      return eventSubscriptionManager.findConditionalStartEventSubscription();
    }
  }

  protected List<ConditionHandlerResult> evaluateConditionStartByProcessDefinitionId(CommandContext commandContext, ConditionSet conditionSet,
      String processDefinitionId) {
    DeploymentCache deploymentCache = commandContext.getProcessEngineConfiguration().getDeploymentCache();
    ProcessDefinitionEntity processDefinition = deploymentCache.findDeployedProcessDefinitionById(processDefinitionId);

    List<ConditionHandlerResult> results = new ArrayList<ConditionHandlerResult>();

    if (processDefinition != null && !processDefinition.isSuspended()) {
      List<ActivityImpl> activities = findActivities(processDefinition);
      for (ActivityImpl activity : activities) {
        if (evaluateCondition(conditionSet, activity)) {
          results.add(new ConditionHandlerResult(processDefinition, activity));
        }
      }
    }
    return results;
  }

  protected List<ActivityImpl> findActivities(ProcessDefinitionEntity processDefinition) {
    List<ActivityImpl> activities = new ArrayList<ActivityImpl>();
    for (EventSubscriptionDeclaration declaration : ConditionalEventDefinition.getDeclarationsForScope(processDefinition).values()) {
      if (isConditionStartEventWithName(declaration)) {
        activities.add(((ConditionalEventDefinition) declaration).getConditionalActivity());
      }
    }
    return activities;
  }

  protected boolean isConditionStartEventWithName(EventSubscriptionDeclaration declaration) {
    return EventType.CONDITONAL.name().equals(declaration.getEventType()) && declaration.isStartEvent();
  }

  protected boolean evaluateCondition(ConditionSet conditionSet, ActivityImpl activity) {
    ExecutionEntity temporaryExecution = new ExecutionEntity();
    temporaryExecution.initializeVariableStore(conditionSet.getVariables());
    temporaryExecution.setProcessDefinition(activity.getProcessDefinition());

    ConditionalEventDefinition conditionalEventDefinition = activity.getProperties().get(BpmnProperties.CONDITIONAL_EVENT_DEFINITION);
    try {
      if (conditionalEventDefinition.evaluate(temporaryExecution)) {
        return (conditionalEventDefinition.getVariableName() == null || conditionSet.getVariables().containsKey(conditionalEventDefinition.getVariableName()))
                && conditionalEventDefinition.evaluate(temporaryExecution);
      }
    } catch (ProcessEngineException e) {
      if (e.getCause() instanceof PropertyNotFoundException) {
        LOG.debugConditionEvaluation(e.getMessage());
      } else {
        throw e;
      }
    }
    return false;
  }

}
