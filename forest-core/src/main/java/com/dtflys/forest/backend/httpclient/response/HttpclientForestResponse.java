package com.dtflys.forest.backend.httpclient.response;

import com.dtflys.forest.backend.ContentType;
import com.dtflys.forest.exceptions.ForestRuntimeException;
import com.dtflys.forest.http.ForestRequest;
import com.dtflys.forest.http.ForestResponse;
import com.dtflys.forest.utils.StringUtils;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * @author gongjun[jun.gong@thebeastshop.com]
 * @since 2018-02-26 15:37
 */
public class HttpclientForestResponse extends ForestResponse {

    private final HttpResponse httpResponse;

    private final HttpEntity entity;

    private byte[] bytes;


    public HttpclientForestResponse(ForestRequest request, HttpResponse httpResponse, HttpEntity entity, Date requestTime, Date responseTime) {
        super(request, requestTime, responseTime);
        this.httpResponse = httpResponse;
        this.entity = entity;
        if (httpResponse != null) {
            setupHeaders();
            StatusLine statusLine = httpResponse.getStatusLine();
            this.statusCode = statusLine.getStatusCode();
            this.reasonPhrase = statusLine.getReasonPhrase();
            if (entity != null) {
                Header type = entity.getContentType();
                if (type != null) {
                    this.contentType = new ContentType(type.getValue());
                }
                Header encoding = entity.getContentEncoding();
                this.contentLength = entity.getContentLength();
                // 是否将Response数据按GZIP来解压
                isGzip = request.isDecompressResponseGzipEnabled();
                if (StringUtils.isNotBlank(request.getResponseEncode())) {
                    this.contentEncoding = request.getResponseEncode();
                } else if (contentType != null) {
                    this.contentEncoding = this.contentType.getCharset();
                }
                if (this.contentEncoding == null && encoding != null) {
                    this.contentEncoding = encoding.getValue();
                    Charset charset;
                    try {
                        charset = Charset.forName(contentEncoding);
                    } catch (Throwable th) {
                        charset = StandardCharsets.UTF_8;
                    }
                    if (!charset.name().equals(this.contentEncoding)) {
                        this.contentEncoding = null;
                    }
                }

                this.content = buildContent();
            } else {
                this.bytes = new byte[0];
                this.content = "";
            }
        } else {
            this.statusCode = -1;
        }
    }

    private void setupHeaders() {
        if (httpResponse != null) {
            HeaderIterator it = httpResponse.headerIterator();
            if (it != null) {
                for (; it.hasNext(); ) {
                    Header header = it.nextHeader();
                    headers.addHeader(header.getName(), header.getValue());
                }
            }
        }
    }

    public HttpResponse getHttpResponse() {
        return httpResponse;
    }

    @Override
    public boolean isReceivedResponseData() {
        return entity != null || bytes != null;
    }

    private String buildContent() {
        if (content == null) {
            if (contentType == null || contentType.isEmpty()) {
                content = readContentAsString();
            } else if (!request.isDownloadFile() && contentType.canReadAsString()) {
                content = readContentAsString();
           } else if (contentType.canReadAsBinaryStream()) {
                StringBuilder builder = new StringBuilder();
                builder.append("[content-type: ")
                        .append(contentType);
                if (contentEncoding != null) {
                    builder.append("; encoding: ")
                            .append(contentEncoding);
                }
                builder.append("; length: ")
                        .append(contentLength)
                        .append("]");
                return builder.toString();
            }
        }
        return content;
    }

    private String readContentAsString() {
        try {
            InputStream inputStream = entity.getContent();
            if (inputStream == null) {
                return null;
            }
            bytes = IOUtils.toByteArray(inputStream);
            return byteToString(bytes);
        } catch (IOException e) {
            throw new ForestRuntimeException(e);
        }
    }

    @Override
    public InputStream getInputStream() throws Exception {
        if (this.contentLength > Integer.MAX_VALUE) {
            return entity.getContent();
        }
        return new ByteArrayInputStream(getByteArray());
    }

    @Override
    public byte[] getByteArray() throws IOException {
        if (bytes == null) {
            if (entity == null) {
                return null;
            } else {
                bytes = EntityUtils.toByteArray(entity);
            }
        }
        return bytes;
    }

}
