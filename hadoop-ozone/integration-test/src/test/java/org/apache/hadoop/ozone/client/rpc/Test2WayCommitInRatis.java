/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.  The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.apache.hadoop.ozone.client.rpc;

import org.apache.hadoop.conf.StorageUnit;
import org.apache.hadoop.hdds.HddsConfigKeys;
import org.apache.hadoop.hdds.conf.DatanodeRatisServerConfig;
import org.apache.hadoop.hdds.conf.OzoneConfiguration;
import org.apache.hadoop.hdds.protocol.proto.HddsProtos;
import org.apache.hadoop.hdds.ratis.RatisHelper;
import org.apache.hadoop.hdds.scm.XceiverClientManager;
import org.apache.hadoop.hdds.scm.XceiverClientRatis;
import org.apache.hadoop.hdds.scm.XceiverClientSpi;
import org.apache.hadoop.hdds.scm.XceiverClientReply;
import org.apache.hadoop.hdds.scm.container.common.helpers.ContainerWithPipeline;
import org.apache.hadoop.hdds.scm.pipeline.Pipeline;
import org.apache.hadoop.hdds.scm.protocolPB.
        StorageContainerLocationProtocolClientSideTranslatorPB;
import org.apache.hadoop.ozone.HddsDatanodeService;
import org.apache.hadoop.ozone.MiniOzoneCluster;
import org.apache.hadoop.ozone.OzoneConfigKeys;
import org.apache.hadoop.ozone.OzoneConsts;
import org.apache.hadoop.ozone.client.ObjectStore;
import org.apache.hadoop.ozone.client.OzoneClient;
import org.apache.hadoop.ozone.client.OzoneClientFactory;
import org.apache.hadoop.ozone.container.ContainerTestHelper;
import org.apache.hadoop.test.GenericTestUtils;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.apache.hadoop.hdds.scm.ScmConfigKeys.
        OZONE_SCM_STALENODE_INTERVAL;

/**
 * This class tests the 2 way commit in Ratis.
 */
public class Test2WayCommitInRatis {

  private MiniOzoneCluster cluster;
  private OzoneClient client;
  private ObjectStore objectStore;
  private String volumeName;
  private String bucketName;
  private int chunkSize;
  private int flushSize;
  private int maxFlushSize;
  private int blockSize;
  private StorageContainerLocationProtocolClientSideTranslatorPB
      storageContainerLocationClient;

  /**
   * Create a MiniDFSCluster for testing.
   * <p>
   * Ozone is made active by setting OZONE_ENABLED = true
   *
   * @throws IOException
   */
  private void startCluster(OzoneConfiguration conf) throws Exception {
    chunkSize = 100;
    flushSize = 2 * chunkSize;
    maxFlushSize = 2 * flushSize;
    blockSize = 2 * maxFlushSize;

    conf.setTimeDuration(
        OzoneConfigKeys.DFS_RATIS_CLIENT_REQUEST_RETRY_INTERVAL_KEY,
        1, TimeUnit.SECONDS);

    // Make sure the pipeline does not get destroyed quickly
    conf.setTimeDuration(HddsConfigKeys.HDDS_HEARTBEAT_INTERVAL,
            60, TimeUnit.SECONDS);
    conf.setTimeDuration(OZONE_SCM_STALENODE_INTERVAL, 60000,
            TimeUnit.SECONDS);
    conf.setTimeDuration(
            RatisHelper.HDDS_DATANODE_RATIS_SERVER_PREFIX_KEY + "." +
                    DatanodeRatisServerConfig.RATIS_SERVER_REQUEST_TIMEOUT_KEY,
            3, TimeUnit.SECONDS);
    conf.setTimeDuration(
            RatisHelper.HDDS_DATANODE_RATIS_SERVER_PREFIX_KEY + "." +
                    DatanodeRatisServerConfig.
                            RATIS_SERVER_WATCH_REQUEST_TIMEOUT_KEY,
            10, TimeUnit.SECONDS);
    conf.setTimeDuration(
            RatisHelper.HDDS_DATANODE_RATIS_CLIENT_PREFIX_KEY+ "." +
                    "rpc.request.timeout",
            3, TimeUnit.SECONDS);
    conf.setTimeDuration(
            RatisHelper.HDDS_DATANODE_RATIS_CLIENT_PREFIX_KEY+ "." +
                    "watch.request.timeout",
            10, TimeUnit.SECONDS);

    conf.setQuietMode(false);
    cluster = MiniOzoneCluster.newBuilder(conf)
        .setNumDatanodes(3)
        .setBlockSize(blockSize)
        .setChunkSize(chunkSize)
        .setStreamBufferFlushSize(flushSize)
        .setStreamBufferMaxSize(maxFlushSize)
        .setStreamBufferSizeUnit(StorageUnit.BYTES)
        .build();
    cluster.waitForClusterToBeReady();
    // the easiest way to create an open container is creating a key
    client = OzoneClientFactory.getRpcClient(conf);
    objectStore = client.getObjectStore();
    volumeName = "watchforcommithandlingtest";
    bucketName = volumeName;
    objectStore.createVolume(volumeName);
    objectStore.getVolume(volumeName).createBucket(bucketName);
    storageContainerLocationClient = cluster
        .getStorageContainerLocationClient();
  }


  /**
   * Shutdown MiniDFSCluster.
   */
  private void shutdown() {
    if (cluster != null) {
      cluster.shutdown();
    }
  }


  @Test
  public void test2WayCommitForRetryfailure() throws Exception {
    OzoneConfiguration conf = new OzoneConfiguration();
    conf.setInt(OzoneConfigKeys.DFS_RATIS_CLIENT_REQUEST_MAX_RETRIES_KEY, 15);
    startCluster(conf);
    GenericTestUtils.LogCapturer logCapturer =
        GenericTestUtils.LogCapturer.captureLogs(XceiverClientRatis.LOG);
    XceiverClientManager clientManager = new XceiverClientManager(conf);

    ContainerWithPipeline container1 = storageContainerLocationClient
        .allocateContainer(HddsProtos.ReplicationType.RATIS,
            3, OzoneConsts.OZONE);
    XceiverClientSpi xceiverClient = clientManager
        .acquireClient(container1.getPipeline());
    Assert.assertEquals(1, xceiverClient.getRefcount());
    Assert.assertEquals(container1.getPipeline(),
        xceiverClient.getPipeline());
    Pipeline pipeline = xceiverClient.getPipeline();
    XceiverClientRatis ratisClient = (XceiverClientRatis) xceiverClient;
    XceiverClientReply reply = xceiverClient.sendCommandAsync(
        ContainerTestHelper.getCreateContainerRequest(
            container1.getContainerInfo().getContainerID(),
            xceiverClient.getPipeline()));
    reply.getResponse().get();
    Assert.assertEquals(3, ratisClient.getCommitInfoMap().size());
    // wait for the container to be created on all the nodes
    xceiverClient.watchForCommit(reply.getLogIndex());
    for (HddsDatanodeService dn : cluster.getHddsDatanodes()) {
      // shutdown the ratis follower
      if (ContainerTestHelper.isRatisFollower(dn, pipeline)) {
        cluster.shutdownHddsDatanode(dn.getDatanodeDetails());
        break;
      }
    }
    reply = xceiverClient.sendCommandAsync(ContainerTestHelper
        .getCloseContainer(pipeline,
            container1.getContainerInfo().getContainerID()));
    reply.getResponse().get();
    xceiverClient.watchForCommit(reply.getLogIndex());

    // commitInfo Map will be reduced to 2 here
    Assert.assertEquals(2, ratisClient.getCommitInfoMap().size());
    clientManager.releaseClient(xceiverClient, false);
    Assert.assertTrue(logCapturer.getOutput().contains("3 way commit failed"));
    Assert
        .assertTrue(logCapturer.getOutput().contains("Committed by majority"));
    logCapturer.stopCapturing();
    shutdown();
  }
}
