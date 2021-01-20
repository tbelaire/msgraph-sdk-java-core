package com.microsoft.graph.content;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonParseException;

import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

/**
 * Represents the response of a batch request
 */
public class MSBatchResponseContent {

    private final Protocol protocol;
    private final String message;
    private LinkedHashMap<String, Request> batchRequestsHashMap = new LinkedHashMap<>();
    private JsonArray batchResponseArray;
    private String nextLink;

    /**
     * @param batchResponse OkHttp batch response on execution of batch requests
     */
    public MSBatchResponseContent(@Nullable final Response batchResponse) {
        update(batchResponse);
        this.message = batchResponse.message();
        this.protocol = batchResponse.protocol();
    }
    /**
     * intantiates a new response
     * internal only, used when the content executes the requests
     * @param baseUrl the base service URL without a trailing slash
     * @param batchRequestData the batch request payload data as a JSON string
     * @param batchResponseData the batch response body as a JSON string
     */
    protected MSBatchResponseContent(@Nonnull final String baseUrl, @Nonnull final String batchRequestData, @Nonnull final String batchResponseData) {
        this.protocol = Protocol.HTTP_1_1;
        this.message = "OK";
        final Map<String, Request> requestMap = createBatchRequestsHashMap(baseUrl, JsonParser.parseString(batchRequestData).getAsJsonObject());
        if (requestMap != null)
            batchRequestsHashMap.putAll(requestMap);
        updateFromResponseBody(batchResponseData);
    }

    /**
     * Returns OkHttp Response of given request Id
     *
     * @param requestId Request Id of batch step
     *
     * @return OkHttp Response corresponding to requestId
     */
    @Nullable
    public Response getResponseById(@Nonnull final String requestId) {
        if (batchResponseArray == null)
            return null;

        final JsonArray responses = batchResponseArray;

        for (final JsonElement response : responses) {
            if(!response.isJsonObject())
                continue;
            final JsonObject jsonresponse = response.getAsJsonObject();
            final JsonElement idElement = jsonresponse.get("id");
            if (idElement != null && idElement.isJsonPrimitive()) {
                final String id = idElement.getAsString();
                if (id.compareTo(requestId) == 0) {
                    final Response.Builder builder = new Response.Builder();

                    // Put corresponding request into the constructed response
                    builder.request(batchRequestsHashMap.get(requestId));
                    // copy protocol and message same as of batch response
                    builder.protocol(protocol);
                    builder.message(message);

                    // Put status code of the corresponding request in JsonArray
                    final JsonElement statusElement = jsonresponse.get("status");
                    if (statusElement != null && statusElement.isJsonPrimitive()) {
                        builder.code(statusElement.getAsInt());
                    }

                    // Put body from response array for corresponding id into constructing response
                    final JsonElement jsonBodyElement = jsonresponse.get("body");
                    if (jsonBodyElement != null && jsonBodyElement.isJsonObject()) {
                        final JsonObject JsonObject = jsonBodyElement.getAsJsonObject();
                        final String bodyAsString = JsonObject.toString();
                        final ResponseBody responseBody = ResponseBody
                                .create(MediaType.parse("application/json; charset=utf-8"), bodyAsString);
                        builder.body(responseBody);
                    }

                    // Put headers from response array for corresponding id into constructing
                    // response
                    final JsonElement jsonheadersElement = jsonresponse.get("headers");
                    if (jsonheadersElement != null && jsonheadersElement.isJsonObject()) {
                        final JsonObject jsonheaders = jsonheadersElement.getAsJsonObject();
                        for (final String key : jsonheaders.keySet()) {
                            final JsonElement strValueElement = jsonheaders.get(key);
                            if (strValueElement != null && strValueElement.isJsonPrimitive()) {
                                final String strvalue = strValueElement.getAsString();
                                for (final String value : strvalue.split(";")) {
                                    builder.header(key, value);
                                }
                            }
                        }
                    }
                    return builder.build();
                }
            }
        }
        return null;
    }

    /**
     * Get map of id and responses
     *
     * @return responses in Map of id and response
     */
    @Nonnull
    public Map<String, Response> getResponses() {
        if (batchResponseArray == null)
            return null;
        final Map<String, Response> responsesMap = new LinkedHashMap<>();
        for (final String id : batchRequestsHashMap.keySet()) {
            responsesMap.put(id, getResponseById(id));
        }
        return responsesMap;
    }

    /**
     * Get iterator over the responses
     *
     * @return iterator for responses
     */
    @Nullable
    public Iterator<Map.Entry<String, Response>> getResponsesIterator() {
        final Map<String, Response> responsesMap = getResponses();
        return responsesMap != null ? responsesMap.entrySet().iterator() : null;
    }

    /**
     * Updates the response content using the raw http response object
     * @param batchResponse the response from the service.
     */
    public void update(@Nonnull final Response batchResponse) {
        if (batchResponse == null)
            throw new IllegalArgumentException("Batch Response cannot be null");

        final Map<String, Request> requestMap = createBatchRequestsHashMap(batchResponse);
        if (requestMap != null)
            batchRequestsHashMap.putAll(requestMap);

        if (batchResponse.body() != null) {
            try {
                final String batchResponseData = batchResponse.body().string();
                if (batchResponseData != null) {
                    updateFromResponseBody(batchResponseData);
                }
            } catch (final IOException e) {
                e.printStackTrace();
            }
        }
    }
    private void updateFromResponseBody(@Nonnull final String batchResponseData) {
        final JsonObject batchResponseObj = stringToJSONObject(batchResponseData);
        if (batchResponseObj != null) {

            final JsonElement nextLinkElement = batchResponseObj.get("@odata.nextLink");
            if (nextLinkElement != null && nextLinkElement.isJsonPrimitive())
                nextLink = nextLinkElement.getAsString();

            if (batchResponseArray == null)
                batchResponseArray = new JsonArray();

            final JsonElement responseArrayElement = batchResponseObj.get("responses");
            if (responseArrayElement != null && responseArrayElement.isJsonArray()) {
                final JsonArray responseArray = responseArrayElement.getAsJsonArray();
                batchResponseArray.addAll(responseArray);
            }
        }
    }

    /**
     * @return nextLink of batch response
     */
    @Nullable
    public String nextLink() {
        return nextLink;
    }

    private Map<String, Request> createBatchRequestsHashMap(final Response batchResponse) {
        if (batchResponse == null)
            return null;
        try {
            final JsonObject requestJSONObject = requestBodyToJSONObject(batchResponse.request());
            final String baseUrl = batchResponse.request().url().toString().replace("$batch", "");
            return createBatchRequestsHashMap(baseUrl, requestJSONObject);
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }
    @Nullable
    protected Map<String, Request> createBatchRequestsHashMap(@Nonnull final String baseUrl, @Nonnull final JsonObject requestJSONObject) {
        if(baseUrl == null || baseUrl == "" || requestJSONObject == null) {
            return null;
        }
        try {
            final Map<String, Request> batchRequestsHashMap = new LinkedHashMap<>();
            final JsonElement requestArrayElement = requestJSONObject.get("requests");
            if (requestArrayElement != null && requestArrayElement.isJsonArray()) {
                final JsonArray requestArray = requestArrayElement.getAsJsonArray();
                for (final JsonElement item : requestArray) {
                    if(!item.isJsonObject())
                        continue;
                    final JsonObject requestObject = item.getAsJsonObject();

                    final Request.Builder builder = new Request.Builder();

                    final JsonElement urlElement = requestObject.get("url");
                    if (urlElement != null && urlElement.isJsonPrimitive()) {
                        final StringBuilder fullUrl = new StringBuilder(baseUrl);
                        fullUrl.append(urlElement.getAsString());
                        builder.url(fullUrl.toString());
                    }
                    final JsonElement jsonHeadersElement = requestObject.get("headers");
                    if (jsonHeadersElement != null && jsonHeadersElement.isJsonObject()) {
                        final JsonObject jsonheaders = jsonHeadersElement.getAsJsonObject();
                        for (final String key : jsonheaders.keySet()) {
                            final JsonElement strvalueElement = jsonheaders.get(key);
                            if (strvalueElement != null && strvalueElement.isJsonPrimitive()) {
                                final String strvalue = strvalueElement.getAsString();
                                for (final String value : strvalue.split("; ")) {
                                    builder.header(key, value);
                                }
                            }
                        }
                    }
                    final JsonElement jsonBodyElement = requestObject.get("body");
                    final JsonElement jsonMethodElement = requestObject.get("method");
                    if (jsonBodyElement != null && jsonMethodElement != null
                        && jsonBodyElement.isJsonObject() && jsonMethodElement.isJsonPrimitive()) {
                        final JsonObject JsonObject = jsonBodyElement.getAsJsonObject();
                        final String bodyAsString = JsonObject.toString();
                        final RequestBody requestBody = RequestBody
                                .create(MediaType.parse("application/json; charset=utf-8"), bodyAsString);
                        builder.method(jsonMethodElement.getAsString(), requestBody);
                    } else if (jsonMethodElement != null) {
                        builder.method(jsonMethodElement.getAsString(), null);
                    }
                    final JsonElement jsonIdElement = requestObject.get("id");
                    if (jsonIdElement != null && jsonIdElement.isJsonPrimitive()) {
                        batchRequestsHashMap.put(jsonIdElement.getAsString(), builder.build());
                    }
                }
            }
            return batchRequestsHashMap;

        } catch (JsonParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    private JsonObject stringToJSONObject(final String input) {
        JsonObject JsonObject = null;
        try {
            if (input != null) {
                JsonObject = JsonParser.parseString(input).getAsJsonObject();
            }
        } catch (final Exception e) {
            e.printStackTrace();
        }
        return JsonObject;
    }

    private JsonObject requestBodyToJSONObject(final Request request) throws IOException, JsonParseException {
        if (request == null || request.body() == null)
            return null;
        final Request copy = request.newBuilder().build();
        final Buffer buffer = new Buffer();
        copy.body().writeTo(buffer);
        final String requestBody = buffer.readUtf8();
        final JsonObject JsonObject = JsonParser.parseString(requestBody).getAsJsonObject();
        return JsonObject;
    }
}
