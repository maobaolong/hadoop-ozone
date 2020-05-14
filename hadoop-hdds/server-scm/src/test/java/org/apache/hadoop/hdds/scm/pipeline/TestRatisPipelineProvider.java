/*
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

package org.apache.hadoop.hdds.scm.pipeline;

import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.protocol.DatanodeDetails;
import org.apache.hadoop.hdds.protocol.MockDatanodeDetails;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos;
import org.apache.hadoop.hdds.scm.ScmConfigKeys;
import org.apache.hadoop.hdds.scm.container.MockNodeManager;
import org.apache.hadoop.hdds.scm.node.NodeManager;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.collections.CollectionUtils.intersection;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test for RatisPipelineProvider.
 */
public class TestRatisPipelineProvider {

  private static final HddsProtos.ReplicationType REPLICATION_TYPE =
      HddsProtos.ReplicationType.RATIS;

  private NodeManager nodeManager;
  private PipelineProvider provider;
  private PipelineStateManager stateManager;
  private OzoneConfiguration conf;


  public void init(int maxPipelinePerNode) throws Exception {
    nodeManager = new MockNodeManager(true, 10);
    conf = new OzoneConfiguration();
    conf.setInt(ScmConfigKeys.OZONE_DATANODE_PIPELINE_LIMIT,
        maxPipelinePerNode);
    stateManager = new PipelineStateManager();
    provider = new MockRatisPipelineProvider(nodeManager,
        stateManager, conf);
  }

  private void createPipelineAndAssertions(
          int replication) throws IOException {
    Pipeline pipeline = provider.create(replication);
    assertPipelineProperties(pipeline, replication, REPLICATION_TYPE,
        Pipeline.PipelineState.ALLOCATED);
    stateManager.addPipeline(pipeline);
    nodeManager.addPipeline(pipeline);

    Pipeline pipeline1 = provider.create(replication);
    assertPipelineProperties(pipeline1, replication, REPLICATION_TYPE,
        Pipeline.PipelineState.ALLOCATED);
    // New pipeline should not overlap with the previous created pipeline
    assertTrue(
        intersection(pipeline.getNodes(), pipeline1.getNodes())
            .size() < replication);
    if (pipeline.getReplication() == 3) {
      assertNotEquals(pipeline.getNodeSet(), pipeline1.getNodeSet());
    }
    stateManager.addPipeline(pipeline1);
    nodeManager.addPipeline(pipeline1);
  }

  @Test
  public void testCreatePipelineWithFactor() throws Exception {
    init(1);
    int replication = 3;
    Pipeline pipeline = provider.create(replication);
    assertPipelineProperties(pipeline, replication, REPLICATION_TYPE,
        Pipeline.PipelineState.ALLOCATED);
    stateManager.addPipeline(pipeline);

    replication = 1;
    Pipeline pipeline1 = provider.create(replication);
    assertPipelineProperties(pipeline1, replication, REPLICATION_TYPE,
        Pipeline.PipelineState.ALLOCATED);
    stateManager.addPipeline(pipeline1);
    // With enough pipeline quote on datanodes, they should not share
    // the same set of datanodes.
    assertNotEquals(pipeline.getNodeSet(), pipeline1.getNodeSet());
  }

  @Test
  public void testCreatePipelineWithFactorThree() throws Exception {
    init(1);
    createPipelineAndAssertions(3);
  }

  @Test
  public void testCreatePipelineWithFactorOne() throws Exception {
    init(1);
    createPipelineAndAssertions(1);
  }

  private List<DatanodeDetails> createListOfNodes(int nodeCount) {
    List<DatanodeDetails> nodes = new ArrayList<>();
    for (int i = 0; i < nodeCount; i++) {
      nodes.add(MockDatanodeDetails.randomDatanodeDetails());
    }
    return nodes;
  }

  @Test
  public void testCreatePipelineWithNodes() throws Exception {
    init(1);
    int replication = 3;
    Pipeline pipeline =
        provider.create(replication, createListOfNodes(replication));
    assertPipelineProperties(pipeline, replication, REPLICATION_TYPE,
        Pipeline.PipelineState.OPEN);

    replication = 1;
    pipeline = provider.create(replication, createListOfNodes(replication));
    assertPipelineProperties(pipeline, replication, REPLICATION_TYPE,
        Pipeline.PipelineState.OPEN);
  }

  @Test
  public void testCreateFactorTHREEPipelineWithSameDatanodes()
      throws Exception {
    init(2);
    List<DatanodeDetails> healthyNodes = nodeManager
        .getNodes(HddsProtos.NodeState.HEALTHY).stream()
        .limit(3).collect(Collectors.toList());

    Pipeline pipeline1 = provider.create(
        3, healthyNodes);
    Pipeline pipeline2 = provider.create(
        3, healthyNodes);

    Assert.assertEquals(pipeline1.getNodeSet(), pipeline2.getNodeSet());
  }

  @Test
  public void testCreatePipelinesDnExclude() throws Exception {

    int maxPipelinePerNode = 2;
    init(maxPipelinePerNode);
    List<DatanodeDetails> healthyNodes =
        nodeManager.getNodes(HddsProtos.NodeState.HEALTHY);

    Assume.assumeTrue(healthyNodes.size() == 8);

    int replication = 3;

    // Use up first 3 DNs for an open pipeline.
    List<DatanodeDetails> dns = healthyNodes.subList(0, 3);
    for (int i = 0; i < maxPipelinePerNode; i++) {
      // Saturate pipeline counts on all the 1st 3 DNs.
      addPipeline(dns, replication,
          Pipeline.PipelineState.OPEN, REPLICATION_TYPE);
    }
    Set<DatanodeDetails> membersOfOpenPipelines = new HashSet<>(dns);

    // Use up next 3 DNs for a closed pipeline.
    dns = healthyNodes.subList(3, 6);
    addPipeline(dns, replication,
        Pipeline.PipelineState.CLOSED, REPLICATION_TYPE);
    Set<DatanodeDetails> membersOfClosedPipelines = new HashSet<>(dns);

    // only 2 healthy DNs left that are not part of any pipeline
    Pipeline pipeline = provider.create(replication);
    assertPipelineProperties(pipeline, replication, REPLICATION_TYPE,
        Pipeline.PipelineState.ALLOCATED);
    nodeManager.addPipeline(pipeline);
    stateManager.addPipeline(pipeline);


    List<DatanodeDetails> nodes = pipeline.getNodes();

    assertTrue(
        "nodes of new pipeline cannot be all from open pipelines",
        nodes.stream().noneMatch(membersOfOpenPipelines::contains));

    assertTrue(
        "at least 1 node should have been from members of closed pipelines",
        nodes.stream().anyMatch(membersOfClosedPipelines::contains));
  }

  private static void assertPipelineProperties(
      Pipeline pipeline, int replication,
      HddsProtos.ReplicationType expectedReplicationType,
      Pipeline.PipelineState expectedState) {
    assertEquals(expectedState, pipeline.getPipelineState());
    assertEquals(expectedReplicationType, pipeline.getType());
    assertEquals(replication, pipeline.getReplication());
    assertEquals(replication, pipeline.getNodes().size());
  }

  private void addPipeline(
      List<DatanodeDetails> dns, int replication,
      Pipeline.PipelineState open, HddsProtos.ReplicationType replicationType)
      throws IOException {
    Pipeline openPipeline = Pipeline.newBuilder()
        .setType(replicationType)
        .setReplication(replication)
        .setNodes(dns)
        .setState(open)
        .setId(PipelineID.randomId())
        .build();

    stateManager.addPipeline(openPipeline);
    nodeManager.addPipeline(openPipeline);
  }
}
