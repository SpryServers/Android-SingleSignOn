/*
 * Nextcloud SingleSignOn
 *
 * @author David Luhmer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.android.sso.api;

import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;
import com.nextcloud.android.sso.aidl.NextcloudRequest;
import com.nextcloud.android.sso.model.SingleSignOnAccount;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.Type;

import io.reactivex.Observable;
import io.reactivex.annotations.NonNull;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

public class NextcloudAPI {

    private static final String TAG = NextcloudAPI.class.getCanonicalName();

    private NetworkRequest networkRequest;
    private Gson gson;


    @Documented
    @Target(METHOD)
    @Retention(RUNTIME)
    public @interface FollowRedirects { }

    public interface ApiConnectedListener {
        void onConnected();
        void onError(Exception ex);
    }


    public NextcloudAPI(@NonNull Context context, @NonNull SingleSignOnAccount account, @NonNull Gson gson, @NonNull ApiConnectedListener callback) {
        this(gson, new AidlNetworkRequest(context, account, callback));
    }

    public NextcloudAPI(Gson gson, NetworkRequest networkRequest) {
        this.gson = gson;
        this.networkRequest = networkRequest;

        new Thread() {
            @Override
            public void run() {
                NextcloudAPI.this.networkRequest.connectApiWithBackoff();
            }
        }.start();
    }

    public void stop() {
        gson = null;
        networkRequest.stop();
    }

    public <T> Observable<T> performRequestObservable(final Type type, final NextcloudRequest request) {
        return Observable.fromPublisher( s-> {
            try {
                s.onNext(performRequest(type, request));
                s.onComplete();
            } catch (Exception e) {
                s.onError(e);
            }
        });
    }

    public <T> T performRequest(final @NonNull Type type, NextcloudRequest request) throws Exception {
        Log.d(TAG, "performRequest() called with: type = [" + type + "], request = [" + request + "]");

        T result = null;
        try (InputStream os = performNetworkRequest(request);
             Reader targetReader = new InputStreamReader(os)) {
            if (type != Void.class) {
                result = gson.fromJson(targetReader, type);
                if (result != null) {
                    Log.d(TAG, result.toString());
                }
            }
        }
        return result;
    }


     /**
     * The InputStreams needs to be closed after reading from it
     *
     * @param request {@link NextcloudRequest} request to be executed on server via Files app
     * @return InputStream answer from server as InputStream
     * @throws Exception or SSOException
     */
     public InputStream performNetworkRequest(NextcloudRequest request) throws Exception {
        return networkRequest.performNetworkRequest(request, null);
    }

    public Response performRequestV2(final @NonNull Type type, NextcloudRequest request) throws Exception {
        Log.d(TAG, "performRequest() called with: type = [" + type + "], request = [" + request + "]");

        Response result = null;
        Response response = performNetworkRequestV2(request);
        Reader targetReader = new InputStreamReader(response.getBody());

        if (type != Void.class) {
            result = gson.fromJson(targetReader, type);
            if (result != null) {
                Log.d(TAG, result.toString());
            }

        }
        return result;
    }


    /**
     * The InputStreams needs to be closed after reading from it
     *
     * @param request {@link NextcloudRequest} request to be executed on server via Files app
     * @return InputStream answer from server as InputStream
     * @throws Exception or SSOException
     */
    public Response performNetworkRequestV2(NextcloudRequest request) throws Exception {
        return networkRequest.performNetworkRequestV2(request, null);
    }

    protected Gson getGson() {
        return gson;
    }
}
