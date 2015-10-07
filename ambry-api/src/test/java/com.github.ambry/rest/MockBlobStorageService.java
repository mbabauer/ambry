package com.github.ambry.rest;

import com.github.ambry.clustermap.ClusterMap;
import java.io.IOException;
import java.nio.ByteBuffer;


/**
 * Implementation of the {@link BlobStorageService} that can be used in tests.
 * <p/>
 * Expected to echo back {@link RestMethod} when the request does not define a custom operation. Otherwise used to
 * induce errors and test error handling in the layers above {@link BlobStorageService}.
 */
public class MockBlobStorageService implements BlobStorageService {
  public static String OPERATION_THROW_HANDLING_RUNTIME_EXCEPTION = "blobStorageThrowHandlingRuntimeException";
  public static String OPERATION_THROW_HANDLING_REST_EXCEPTION = "blobStorageThrowHandlingRestException";

  public MockBlobStorageService(ClusterMap clusterMap) {
    // This constructor is around so that this can be instantiated from the NioServerFactory.
    // We might have uses for the arguments in the future.
  }

  @Override
  public void start()
      throws InstantiationException {
  }

  @Override
  public void shutdown() {
  }

  @Override
  public void handleGet(RestRequestInfo restRequestInfo)
      throws RestServiceException {
    doHandleRequest(restRequestInfo);
  }

  @Override
  public void handlePost(RestRequestInfo restRequestInfo)
      throws RestServiceException {
    doHandleRequest(restRequestInfo);
  }

  @Override
  public void handleDelete(RestRequestInfo restRequestInfo)
      throws RestServiceException {
    doHandleRequest(restRequestInfo);
  }

  @Override
  public void handleHead(RestRequestInfo restRequestInfo)
      throws RestServiceException {
    doHandleRequest(restRequestInfo);
  }

  /**
   * Performs any custom operations required by the request (usually tests use this).
   * <p/>
   * All other requests are handled by echoing the {@link RestMethod} back to the client.
   * @param restRequestInfo {@link RestRequestInfo } that defines a piece of the request that needs to be handled.
   * @throws RestServiceException
   */
  private void doHandleRequest(RestRequestInfo restRequestInfo)
      throws RestServiceException {
    String operationType = getOperationType(restRequestInfo.getRestRequestMetadata());
    if (OPERATION_THROW_HANDLING_RUNTIME_EXCEPTION.equals(operationType)) {
      // exception message is operationType so that it can be verified by the test.
      throw new RuntimeException(operationType);
    } else if (OPERATION_THROW_HANDLING_REST_EXCEPTION.equals(operationType)) {
      throw new RestServiceException(operationType, RestServiceErrorCode.InternalServerError);
    } else {
      // NOTE:  If you ever need to implement functionality that cannot go here -
      // Check if RestRequestMetadata is an instance of MockRestRequestMetadata. If it is, you can support any kind of
      // custom function as long as it is implemented in MockRestRequestMetadata or reachable through it as a callback.
      echoRestMethod(restRequestInfo);
    }
  }

  /**
   * Determines the operation desired by the request.
   * @param restRequestMetadata {@link RestRequestMetadata} metadata about the request.
   * @return the operation desired by the request.
   */
  private String getOperationType(RestRequestMetadata restRequestMetadata) {
    String path = restRequestMetadata.getPath();
    return path.startsWith("/") ? path.substring(1, path.length()) : path;
  }

  /**
   * Echoes the {@link RestMethod} defined in {@link RestRequestMetadata} and writes the response to the channel.
   * @param restRequestInfo {@link RestRequestInfo } that defines a piece of the request that needs to be handled.
   * @throws RestServiceException
   */
  private void echoRestMethod(RestRequestInfo restRequestInfo)
      throws RestServiceException {
    RestResponseChannel restResponseChannel = restRequestInfo.getRestResponseChannel();
    RestRequestContent content = restRequestInfo.getRestRequestContent();
    try {
      if (restRequestInfo.isFirstPart()) {
        RestMethod restMethod = restRequestInfo.getRestRequestMetadata().getRestMethod();
        restResponseChannel.setContentType("text/plain; charset=UTF-8");
        restResponseChannel.write(ByteBuffer.wrap(restMethod.toString().getBytes()));
      } else {
        byte[] contentBytes = new byte[content.getContentSize()];
        content.getBytes(0, contentBytes, 0, content.getContentSize());
        restResponseChannel.write(ByteBuffer.wrap(contentBytes));
        if (content.isLast()) {
          restResponseChannel.flush();
          restResponseChannel.onRequestComplete(null, false);
        }
      }
    } catch (IOException e) {
      throw new RestServiceException(e, RestServiceErrorCode.ChannelWriteError);
    }
  }
}