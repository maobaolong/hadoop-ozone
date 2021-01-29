/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.hdds.scm.cli.pipeline;

import com.google.common.base.Strings;
import org.apache.hadoop.hdds.cli.HddsVersionProvider;
import org.apache.hadoop.hdds.scm.cli.ScmSubcommand;
import org.apache.hadoop.hdds.scm.client.ScmClient;
import org.apache.hadoop.hdds.scm.pipeline.Pipeline;
import picocli.CommandLine;

import java.io.IOException;
import java.util.stream.Stream;

/**
 * Handler of list pipelines command.
 */
@CommandLine.Command(
    name = "list",
    description = "List all active pipelines",
    mixinStandardHelpOptions = true,
    versionProvider = HddsVersionProvider.class)
public class ListPipelinesSubcommand extends ScmSubcommand {

  @CommandLine.Option(names = {"-ffc", "--filterByFactor"},
      description = "Filter listed pipelines by Factor(ONE/one)",
      defaultValue = "")
  private String factor;

  @CommandLine.Option(names = {"-fst", "--filterByState"},
      description = "Filter listed pipelines by State(OPEN/CLOSE)",
      defaultValue = "")
  private String state;

  @Override
  public void execute(ScmClient scmClient) throws IOException {
    Stream<Pipeline> stream = scmClient.listPipelines().stream();
    if (!Strings.isNullOrEmpty(factor)) {
      stream = stream.filter(
          p -> p.getFactor().toString().compareToIgnoreCase(factor) == 0);
    }
    if (!Strings.isNullOrEmpty(state)) {
      stream = stream.filter(p -> p.getPipelineState().toString()
          .compareToIgnoreCase(state) == 0);
    }
    stream.forEach(System.out::println);
  }
}
