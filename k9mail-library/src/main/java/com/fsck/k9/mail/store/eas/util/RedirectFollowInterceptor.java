package com.fsck.k9.mail.store.eas.util;


import java.io.IOException;

import com.squareup.okhttp.HttpUrl;
import com.squareup.okhttp.Interceptor;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;

import static java.net.HttpURLConnection.HTTP_MOVED_TEMP;


/**
 * An {@link OkHttpClient} application interceptor that follows 302 redirects.
 * <p>
 * Additional requests will use the same method as the first request. E.g. a POST request with a 302 response will
 * not use GET but do a POST to the new location.
 * </p>
 */
public final class RedirectFollowInterceptor implements Interceptor {
    private final int maxNumberOfRedirects;

    public RedirectFollowInterceptor(int maxNumberOfRedirects) {
        this.maxNumberOfRedirects = maxNumberOfRedirects;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();

        Response response = chain.proceed(request);

        return followRedirectsIfNecessary(chain, request, response);
    }

    private Response followRedirectsIfNecessary(Chain chain, Request request, Response response) throws IOException {
        int redirectCount = 0;
        while (response.code() == HTTP_MOVED_TEMP && redirectCount++ < maxNumberOfRedirects) {
            String location = response.header("Location");
            if (location == null) {
                break;
            }

            HttpUrl newUrl = request.httpUrl().resolve(location);
            if (newUrl == null) {
                break;
            }

            Request additionalRequest = request.newBuilder()
                    .url(newUrl)
                    .removeHeader("Host")
                    .build();

            response = chain.proceed(additionalRequest);
        }

        return response;
    }
}
