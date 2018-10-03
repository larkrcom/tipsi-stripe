package com.gettipsi.stripe;

import android.support.annotation.NonNull;
import android.util.Log;

import com.stripe.android.CustomerSession;
import com.stripe.android.EphemeralKeyProvider;
import com.stripe.android.EphemeralKeyUpdateListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.*;

public class CustomerSessionManager implements EphemeralKeyProvider {

  private String endpoint, authToken;
  private Boolean isInitialized = false;

  private OkHttpClient client = new OkHttpClient();

  private void initCustomerSession() {
    if (isInitialized)
      return;

    CustomerSession.initCustomerSession(this);
    isInitialized = true;
  }

  @Override
  public void createEphemeralKey(@NonNull String apiVersion, @NonNull final EphemeralKeyUpdateListener keyUpdateListener) {
    JSONObject json = new JSONObject();
    try {
      json.put("api_version", apiVersion);
    } catch (JSONException e) {
      // Never happens
    }

    final Request.Builder request = new Request.Builder().url(endpoint)
      .post(RequestBody.create(MediaType.parse("application/json"), json.toString()));

    if (authToken != null)
      request.addHeader("Authorization", "Bearer " + authToken);

    client.newCall(request.build()).enqueue(new Callback() {
      @Override
      public void onFailure(Call call, IOException e) {
        Log.e(getClass().getSimpleName(), "onFailure: Failed request! " + request, e);
        keyUpdateListener.onKeyUpdateFailure(500, "Internal Error!!");
      }

      @Override
      public void onResponse(Call call, Response response) throws IOException {
        ResponseBody body = response.body();
        if (response.isSuccessful()) {
          keyUpdateListener.onKeyUpdate(body.string());
        } else {
          String message = body.string();
          Log.w(getClass().getSimpleName(), "onResponse: Failed fetching ephemeral key! " + message);
          keyUpdateListener.onKeyUpdateFailure(response.code(), message);
        }
      }
    });
  }

  public void updateServerInfo(String authToken, String endpoint) {
    this.authToken = authToken;

    if (endpoint != null)
      this.endpoint = endpoint;
    else
      throw new RuntimeException("Please add an endpoint for the EphemeralKey creation!");

      if (endpoint != null)
        initCustomerSession();
  }

  public void endSession() {
    isInitialized = false;
    CustomerSession.endCustomerSession();
  }

  private static CustomerSessionManager instance;

  public static CustomerSessionManager getInstance() {
    if (instance == null) {
      instance = new CustomerSessionManager();
      instance.initCustomerSession();
      return instance;
    }

    return instance;
  }
}
