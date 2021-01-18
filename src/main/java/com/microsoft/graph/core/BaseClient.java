// ------------------------------------------------------------------------------
// Copyright (c) 2017 Microsoft Corporation
//
// Permission is hereby granted, free of charge, to any person obtaining a copy
// of this software and associated documentation files (the "Software"), to deal
// in the Software without restriction, including without limitation the rights
// to use, copy, modify, merge, publish, distribute, sub-license, and/or sell
// copies of the Software, and to permit persons to whom the Software is
// furnished to do so, subject to the following conditions:
//
// The above copyright notice and this permission notice shall be included in
// all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
// IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
// FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
// AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
// LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
// OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
// THE SOFTWARE.
// ------------------------------------------------------------------------------

package com.microsoft.graph.core;

import com.microsoft.graph.http.IHttpProvider;
import com.microsoft.graph.logger.ILogger;
import com.microsoft.graph.serializer.ISerializer;

import javax.annotation.Nullable;
import javax.annotation.Nonnull;

/**
 * A client that communications with an OData service
 */
public abstract class BaseClient implements IBaseClient {
    /**
     * The HTTP provider instance
     */
    private IHttpProvider httpProvider;

    /**
     * The logger
     */
    private ILogger logger;

    /**
     * The serializer instance
     */
    private ISerializer serializer;

    /**
     * Gets the HTTP provider
     *
     * @return The HTTP provider
     */
    @Override
    @Nullable
    public IHttpProvider getHttpProvider() {
        return httpProvider;
    }

    /**
     * Gets the logger
     *
     * @return The logger
     */
    @Nullable
    public ILogger getLogger() {
        return logger;
    }

    /**
     * Gets the serializer
     *
     * @return The serializer
     */
    @Override
    @Nullable
    public ISerializer getSerializer() {
        return serializer;
    }

    /**
     * Validates this client
     */
    @Override
    public void validate() {
        if (httpProvider == null) {
            throw new NullPointerException("HttpProvider");
        }

        if (serializer == null) {
            throw new NullPointerException("Serializer");
        }
    }

    /**
     * Sets the logger
     *
     * @param logger The logger
     */
    protected void setLogger(@Nonnull final ILogger logger) {
        this.logger = logger;
    }

    /**
     * Sets the HTTP provider
     *
     * @param httpProvider The HTTP provider
     */
    protected void setHttpProvider(@Nonnull final IHttpProvider httpProvider) {
        this.httpProvider = httpProvider;
    }

    /**
     * Sets the serializer
     *
     * @param serializer The serializer
     */
    public void setSerializer(@Nonnull final ISerializer serializer) {
        this.serializer = serializer;
    }
}