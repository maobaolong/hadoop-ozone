/**
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

package org.apache.hadoop.ozone.om.request.key;

import java.util.UUID;
import org.junit.Assert;
import org.junit.Test;

import org.apache.hadoop.hdds.utils.db.BatchOperation;
import org.apache.hadoop.ozone.om.helpers.OmKeyInfo;
import org.apache.hadoop.ozone.om.request.TestOMRequestUtils;
import org.apache.hadoop.ozone.om.response.OMClientResponse;
import org.apache.hadoop.ozone.om.response.key.OMKeyRenameResponse;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos
    .OMRequest;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos
    .KeyArgs;
import org.apache.hadoop.ozone.protocol.proto.OzoneManagerProtocolProtos
    .RenameKeyRequest;

/**
 * Tests RenameKey request.
 */
public class TestOMKeyRenameRequest extends TestOMKeyRequest {

  @Test
  public void testPreExecute() throws Exception {
    doPreExecute(createRenameKeyRequest(UUID.randomUUID().toString()));
  }

  @Test
  public void testValidateAndUpdateCache() throws Exception {
    String toKeyName = UUID.randomUUID().toString();
    OMRequest modifiedOmRequest =
        doPreExecute(createRenameKeyRequest(toKeyName));

    TestOMRequestUtils.addVolumeAndBucketToDB(volumeName, bucketName,
        omMetadataManager);
    TestOMRequestUtils.addKeyToTable(false, volumeName, bucketName, keyName,
        clientID, replicationType, replication, omMetadataManager);

    OMKeyRenameRequest omKeyRenameRequest =
        new OMKeyRenameRequest(modifiedOmRequest);

    OMClientResponse omKeyRenameResponse =
        omKeyRenameRequest.validateAndUpdateCache(ozoneManager, 100L,
            ozoneManagerDoubleBufferHelper);

    Assert.assertEquals(OzoneManagerProtocolProtos.Status.OK,
        omKeyRenameResponse.getOMResponse().getStatus());

    String key = omMetadataManager.getOzoneKey(volumeName, bucketName, keyName);
    // Original key should be deleted, toKey should exist.
    OmKeyInfo omKeyInfo = omMetadataManager.getKeyTable().get(key);

    Assert.assertNull(omKeyInfo);

    omKeyInfo =
        omMetadataManager.getKeyTable().get(omMetadataManager.getOzoneKey(
            volumeName, bucketName, toKeyName));

    Assert.assertNotNull(omKeyInfo);

    // For new key modification time should be updated.

    KeyArgs keyArgs = modifiedOmRequest.getRenameKeyRequest().getKeyArgs();

    Assert.assertEquals(keyArgs.getModificationTime(),
        omKeyInfo.getModificationTime());

    // KeyName should be updated in OmKeyInfo to toKeyName.
    Assert.assertEquals(omKeyInfo.getKeyName(), toKeyName);
  }

  @Test
  public void testValidateAndUpdateCacheWithKeyNotFound() throws Exception {
    String toKeyName = UUID.randomUUID().toString();
    OMRequest modifiedOmRequest =
        doPreExecute(createRenameKeyRequest(toKeyName));

    // Add only volume and bucket entry to DB.

    // In actual implementation we don't check for bucket/volume exists
    // during delete key.
    TestOMRequestUtils.addVolumeAndBucketToDB(volumeName, bucketName,
        omMetadataManager);

    OMKeyRenameRequest omKeyRenameRequest =
        new OMKeyRenameRequest(modifiedOmRequest);

    OMClientResponse omKeyRenameResponse =
        omKeyRenameRequest.validateAndUpdateCache(ozoneManager, 100L,
            ozoneManagerDoubleBufferHelper);

    Assert.assertEquals(OzoneManagerProtocolProtos.Status.KEY_NOT_FOUND,
        omKeyRenameResponse.getOMResponse().getStatus());
  }

  @Test
  public void testValidateAndUpdateCacheWithVolumeNotFound() throws Exception {
    String toKeyName = UUID.randomUUID().toString();
    OMRequest modifiedOmRequest =
        doPreExecute(createRenameKeyRequest(toKeyName));

    OMKeyRenameRequest omKeyRenameRequest =
        new OMKeyRenameRequest(modifiedOmRequest);

    OMClientResponse omKeyRenameResponse =
        omKeyRenameRequest.validateAndUpdateCache(ozoneManager, 100L,
            ozoneManagerDoubleBufferHelper);

    Assert.assertEquals(OzoneManagerProtocolProtos.Status.VOLUME_NOT_FOUND,
        omKeyRenameResponse.getOMResponse().getStatus());
  }

  @Test
  public void testValidateAndUpdateCacheWithBucketNotFound() throws Exception {
    String toKeyName = UUID.randomUUID().toString();
    OMRequest modifiedOmRequest =
        doPreExecute(createRenameKeyRequest(toKeyName));

    // Add only volume entry to DB.
    TestOMRequestUtils.addVolumeToDB(volumeName, omMetadataManager);

    OMKeyRenameRequest omKeyRenameRequest =
        new OMKeyRenameRequest(modifiedOmRequest);

    OMClientResponse omKeyRenameResponse =
        omKeyRenameRequest.validateAndUpdateCache(ozoneManager, 100L,
            ozoneManagerDoubleBufferHelper);

    Assert.assertEquals(OzoneManagerProtocolProtos.Status.BUCKET_NOT_FOUND,
        omKeyRenameResponse.getOMResponse().getStatus());
  }

  @Test
  public void testValidateAndUpdateCacheWithToKeyInvalid() throws Exception {
    String toKeyName = "";
    OMRequest modifiedOmRequest =
        doPreExecute(createRenameKeyRequest(toKeyName));

    // Add only volume and bucket entry to DB.

    // In actual implementation we don't check for bucket/volume exists
    // during delete key.
    TestOMRequestUtils.addVolumeAndBucketToDB(volumeName, bucketName,
        omMetadataManager);

    OMKeyRenameRequest omKeyRenameRequest =
        new OMKeyRenameRequest(modifiedOmRequest);

    OMClientResponse omKeyRenameResponse =
        omKeyRenameRequest.validateAndUpdateCache(ozoneManager, 100L,
            ozoneManagerDoubleBufferHelper);

    Assert.assertEquals(OzoneManagerProtocolProtos.Status.INVALID_KEY_NAME,
        omKeyRenameResponse.getOMResponse().getStatus());
  }

  @Test
  public void testValidateAndUpdateCacheWithFromKeyInvalid() throws Exception {
    String toKeyName = UUID.randomUUID().toString();
    keyName = "";
    OMRequest modifiedOmRequest =
        doPreExecute(createRenameKeyRequest(toKeyName));

    // Add only volume and bucket entry to DB.

    // In actual implementation we don't check for bucket/volume exists
    // during delete key.
    TestOMRequestUtils.addVolumeAndBucketToDB(volumeName, bucketName,
        omMetadataManager);

    OMKeyRenameRequest omKeyRenameRequest =
        new OMKeyRenameRequest(modifiedOmRequest);

    OMClientResponse omKeyRenameResponse =
        omKeyRenameRequest.validateAndUpdateCache(ozoneManager, 100L,
            ozoneManagerDoubleBufferHelper);

    Assert.assertEquals(OzoneManagerProtocolProtos.Status.INVALID_KEY_NAME,
        omKeyRenameResponse.getOMResponse().getStatus());
  }

  /**
   * Test replay of RenameRequest when fromKey does not exist in DB.
   */
  @Test
  public void testReplayRequest() throws Exception {
    String toKeyName = UUID.randomUUID().toString();
    OMRequest modifiedOmRequest = doPreExecute(
        createRenameKeyRequest(toKeyName));

    TestOMRequestUtils.addVolumeAndBucketToDB(volumeName, bucketName,
        omMetadataManager);
    TestOMRequestUtils.addKeyToTableAndCache(volumeName, bucketName,
        keyName, clientID, replicationType, replication, 1L,
        omMetadataManager);

    // Execute RenameRequest
    OMKeyRenameRequest omKeyRenameRequest =
        new OMKeyRenameRequest(modifiedOmRequest);
    OMClientResponse omKeyRenameResponse =
        omKeyRenameRequest.validateAndUpdateCache(ozoneManager, 10L,
            ozoneManagerDoubleBufferHelper);

    // Commit Batch operation to add the transaction to DB
    BatchOperation batchOperation = omMetadataManager.getStore()
        .initBatchOperation();
    omKeyRenameResponse.checkAndUpdateDB(omMetadataManager, batchOperation);
    omMetadataManager.getStore().commitBatchOperation(batchOperation);

    // Replay the RenameRequest.
    OMClientResponse replayResponse = omKeyRenameRequest.validateAndUpdateCache(
        ozoneManager, 10L, ozoneManagerDoubleBufferHelper);

    // Replay should result in Replay response
    Assert.assertEquals(OzoneManagerProtocolProtos.Status.REPLAY,
        replayResponse.getOMResponse().getStatus());
  }

  /**
   * Test replay of RenameRequest when fromKey exists in DB.
   */
  @Test
  public void testReplayRequestWhenFromKeyExists() throws Exception {

    String toKeyName = UUID.randomUUID().toString();
    OMRequest modifiedOmRequest = doPreExecute(
        createRenameKeyRequest(toKeyName));

    TestOMRequestUtils.addVolumeAndBucketToDB(volumeName, bucketName,
        omMetadataManager);
    TestOMRequestUtils.addKeyToTableAndCache(volumeName, bucketName, keyName,
        clientID, replicationType, replication, 1L, omMetadataManager);

    // Execute RenameRequest
    OMKeyRenameRequest omKeyRenameRequest =
        new OMKeyRenameRequest(modifiedOmRequest);
    OMClientResponse omKeyRenameResponse = omKeyRenameRequest
        .validateAndUpdateCache(ozoneManager, 10L,
            ozoneManagerDoubleBufferHelper);

    // Commit Batch operation to add the transaction to DB
    BatchOperation batchOperation = omMetadataManager.getStore()
        .initBatchOperation();
    omKeyRenameResponse.checkAndUpdateDB(omMetadataManager, batchOperation);
    omMetadataManager.getStore().commitBatchOperation(batchOperation);

    // Let's say the fromKey create transaction was also replayed. In this
    // case, fromKey and toKey will both exist in the DB. Replaying the
    // RenameRequest should then delete fromKey but not add toKey again.

    // Replay CreateKey request for fromKey
    TestOMRequestUtils.addKeyToTableAndCache(volumeName, bucketName, keyName,
        clientID, replicationType, replication, 1L, omMetadataManager);

    // Verify fromKey exists in DB
    String fromKey = omMetadataManager.getOzoneKey(volumeName, bucketName,
        keyName);
    OmKeyInfo dbFromKeyInfo = omMetadataManager.getKeyTable().get(fromKey);
    Assert.assertNotNull(dbFromKeyInfo);

    // Replay original RenameRequest
    OMKeyRenameResponse replayResponse =
        (OMKeyRenameResponse) omKeyRenameRequest.validateAndUpdateCache(
            ozoneManager, 10L, ozoneManagerDoubleBufferHelper);

    // This replay response should delete fromKey from DB
    // Replay should result in Replay response
    Assert.assertEquals(OzoneManagerProtocolProtos.Status.OK,
        replayResponse.getOMResponse().getStatus());
    Assert.assertTrue(replayResponse.deleteFromKeyOnly());

    // Commit response to DB
    batchOperation = omMetadataManager.getStore().initBatchOperation();
    replayResponse.addToDBBatch(omMetadataManager, batchOperation);
    omMetadataManager.getStore().commitBatchOperation(batchOperation);

    // Verify fromKey is deleted from DB
    dbFromKeyInfo = omMetadataManager.getKeyTable().get(fromKey);
    Assert.assertNull(dbFromKeyInfo);
  }

  /**
   * This method calls preExecute and verify the modified request.
   * @param originalOmRequest
   * @return OMRequest - modified request returned from preExecute.
   * @throws Exception
   */

  private OMRequest doPreExecute(OMRequest originalOmRequest) throws Exception {
    OMKeyRenameRequest omKeyRenameRequest =
        new OMKeyRenameRequest(originalOmRequest);

    OMRequest modifiedOmRequest = omKeyRenameRequest.preExecute(ozoneManager);

    // Will not be equal, as UserInfo will be set and modification time is
    // set in KeyArgs.
    Assert.assertNotEquals(originalOmRequest, modifiedOmRequest);

    Assert.assertTrue(modifiedOmRequest.getRenameKeyRequest()
        .getKeyArgs().getModificationTime() > 0);

    return modifiedOmRequest;
  }

  /**
   * Create OMRequest which encapsulates RenameKeyRequest.
   * @return OMRequest
   */
  private OMRequest createRenameKeyRequest(String toKeyName) {
    KeyArgs keyArgs = KeyArgs.newBuilder().setKeyName(keyName)
        .setVolumeName(volumeName).setBucketName(bucketName).build();

    RenameKeyRequest renameKeyRequest = RenameKeyRequest.newBuilder()
            .setKeyArgs(keyArgs).setToKeyName(toKeyName).build();

    return OMRequest.newBuilder()
        .setClientId(UUID.randomUUID().toString())
        .setRenameKeyRequest(renameKeyRequest)
        .setCmdType(OzoneManagerProtocolProtos.Type.RenameKey).build();
  }

}
