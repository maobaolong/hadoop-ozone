/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.scm.pipeline.choose.algorithms;

import org.apache.hadoop.hdds.conf.ConfigurationSource;
import org.apache.hadoop.hdds.scm.PipelineChoosePolicy;
import org.apache.hadoop.hdds.scm.ScmConfigKeys;
import org.apache.hadoop.hdds.scm.exceptions.SCMException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;

/**
 * A factory to create pipeline choose policy instance based on configuration
 * property {@link ScmConfigKeys#OZONE_SCM_PIPELINE_CHOOSE_POLICY_IMPL_KEY}.
 */
public final class PipelineChoosePolicyFactory {
  private static final Logger LOG =
      LoggerFactory.getLogger(PipelineChoosePolicyFactory.class);

  private static final Class<? extends PipelineChoosePolicy>
      OZONE_SCM_PIPELINE_CHOOSE_POLICY_IMPL_DEFAULT =
      RandomPipelineChoosePolicy.class;

  private PipelineChoosePolicyFactory() {
  }

  public static PipelineChoosePolicy getPolicy(
      ConfigurationSource conf) throws SCMException {
    final Class<? extends PipelineChoosePolicy> policyClass = conf
        .getClass(ScmConfigKeys.OZONE_SCM_PIPELINE_CHOOSE_POLICY_IMPL_KEY,
            OZONE_SCM_PIPELINE_CHOOSE_POLICY_IMPL_DEFAULT,
            PipelineChoosePolicy.class);
    try {
      return createPipelineChoosePolicyFromClass(policyClass);
    } catch (Exception e) {
      if (policyClass != OZONE_SCM_PIPELINE_CHOOSE_POLICY_IMPL_DEFAULT) {
        LOG.error("Met an exception while create pipeline choose policy "
            + "for the given class " + policyClass.getName()
            + ". Fallback to the default pipeline choose policy "
            + OZONE_SCM_PIPELINE_CHOOSE_POLICY_IMPL_DEFAULT, e);
        return createPipelineChoosePolicyFromClass(
            OZONE_SCM_PIPELINE_CHOOSE_POLICY_IMPL_DEFAULT);
      }
      throw e;
    }
  }

  @NotNull
  private static PipelineChoosePolicy createPipelineChoosePolicyFromClass(
      Class<? extends PipelineChoosePolicy> policyClass) throws SCMException {
    Constructor<? extends PipelineChoosePolicy> constructor;
    try {
      constructor = policyClass.getDeclaredConstructor();
      LOG.info("Create pipeline choose policy of type {}",
          policyClass.getCanonicalName());
    } catch (NoSuchMethodException e) {
      String msg = "Failed to find constructor() for class " +
          policyClass.getCanonicalName();
      LOG.error(msg);
      throw new SCMException(msg,
          SCMException.ResultCodes.FAILED_TO_INIT_PIPELINE_CHOOSE_POLICY);
    }

    try {
      return constructor.newInstance();
    } catch (Exception e) {
      throw new RuntimeException("Failed to instantiate class " +
          policyClass.getCanonicalName() + " for " + e.getMessage());
    }
  }
}