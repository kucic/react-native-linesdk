package io.travelai.linesdk;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.facebook.common.activitylistener.BaseActivityListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;
// Add
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;

// line
import com.linecorp.linesdk.LineAccessToken;
import com.linecorp.linesdk.LineApiResponse;
import com.linecorp.linesdk.LineFriendshipStatus;
import com.linecorp.linesdk.LineIdToken;
import com.linecorp.linesdk.LineProfile;
import com.linecorp.linesdk.Scope;
import com.linecorp.linesdk.api.LineApiClient;
import com.linecorp.linesdk.api.LineApiClientBuilder;
import com.linecorp.linesdk.auth.LineAuthenticationParams;
import com.linecorp.linesdk.auth.LineLoginApi;
import com.linecorp.linesdk.auth.LineLoginResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

public class RNLinesdkModule extends ReactContextBaseJavaModule { 

    private static final int REQUEST_CODE = 1;
    private static final String ERROR = "ERROR";
    private Promise currentPromise;
    private String lineChannelID;
    private LineApiClient lineApiClient;

    private final ReactApplicationContext reactContext;

    public RNLinesdkModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        this.reactContext.addActivityEventListener(_ActivityEventListener);

    }

    @Override
    public String getName() {
        return "RNLinesdk";
    }


    /*
    @ReactMethod
    public void sampleMethod(String stringArgument, int numberArgument, Callback callback) {
        // TODO: Implement some actually useful functionality
        callback.invoke("Received numberArgument: " + numberArgument + " stringArgument: " + stringArgument);
    }
    */

    private LineApiClient getLineApiClient() {
        if (lineApiClient == null) {
            lineApiClient = new LineApiClientBuilder(this.reactContext, this.lineChannelID).build();
        }
        return lineApiClient;
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private WritableMap parseLoginResult(LineLoginResult loginResult) {
        LineIdToken lineIdToken = loginResult.getLineIdToken();
        WritableMap result = Arguments.createMap();
        result.putMap("profile", parseProfile(Objects.requireNonNull(loginResult.getLineProfile())));
        result.putBoolean("friendshipStatusChanged", loginResult.getFriendshipStatusChanged());
        result.putMap("accessToken", parseAccessToken(Objects.requireNonNull(loginResult.getLineCredential()).getAccessToken()));
        if (lineIdToken != null) {
            result.putMap("extraInfo", parseExtraInfo(lineIdToken));
        } else {
            result.putNull("extraInfo");
        }
        return result;
    }

    private WritableMap parseExtraInfo(LineIdToken lineIdToken) {
        WritableMap result = Arguments.createMap();
        result.putString("email", lineIdToken.getEmail());
        result.putString("audience", lineIdToken.getAudience());
        result.putString("birthDate", lineIdToken.getBirthdate());
        result.putString("familyName", lineIdToken.getFamilyName());
        result.putString("familyNamePronunciation", lineIdToken.getFamilyNamePronunciation());
        result.putString("gender", lineIdToken.getGender());
        result.putString("givenName", lineIdToken.getGivenName());
        result.putString("givenNamePronunciation", lineIdToken.getGivenNamePronunciation());
        result.putString("issuer", lineIdToken.getIssuer());
        result.putString("middleName", lineIdToken.getMiddleName());
        result.putString("name", lineIdToken.getName());
        result.putString("nonce", lineIdToken.getNonce());
        result.putString("phoneNumber", lineIdToken.getPhoneNumber());
        result.putString("picture", lineIdToken.getPicture());
        result.putString("subject", lineIdToken.getSubject());
        result.putString("address", lineIdToken.getAddress() == null ? null : lineIdToken.getAddress().toString());
        return result;
    }

    private WritableMap parseProfile(LineProfile profile) {
        WritableMap result = Arguments.createMap();
        result.putString("displayName", profile.getDisplayName());
        result.putString("userID", profile.getUserId());
        result.putString("statusMessage", profile.getStatusMessage());
        if (profile.getPictureUrl() != null) {
            result.putString("pictureURL", profile.getPictureUrl().toString());
        }
        return result;
    }

    private WritableMap parseAccessToken(LineAccessToken accessToken) {
        WritableMap result = Arguments.createMap();
        result.putString("accessToken", accessToken.getTokenString());
        result.putString("expirationDate", Long.toString(accessToken.getExpiresInMillis()));
        return result;
    }

    private final ActivityEventListener _ActivityEventListener = new BaseActivityEventListener() {
        @RequiresApi(api = Build.VERSION_CODES.KITKAT)
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
            super.onActivityResult(activity, requestCode, resultCode, data);
            if (currentPromise != null) {
                final Promise promise = currentPromise;
                currentPromise = null;
                if (requestCode != REQUEST_CODE) {
                    promise.reject(ERROR, "Unsupported request");
                    return;
                }
                final LineLoginResult loginResult = LineLoginApi.getLoginResultFromIntent(data);
                switch (loginResult.getResponseCode()) {
                    case SUCCESS:
                        WritableMap response = Arguments.createMap();
                        response.putString("action", "LOGIN");
                        response.putString("code", loginResult.getResponseCode().name());
                        response.putMap("data", parseLoginResult(loginResult));
                        promise.resolve(response);
                        break;
                    case CANCEL:
                        promise.reject(ERROR, "The request was canceled.");
                        break;
                    case AUTHENTICATION_AGENT_ERROR:
                        promise.reject(ERROR, "An authentication agent error occurred.");
                        break;
                    case SERVER_ERROR:
                        promise.reject(ERROR, "A server error occurred.");
                    case INTERNAL_ERROR:
                        promise.reject(ERROR, "An internal error occurred.");
                    case NETWORK_ERROR:
                        promise.reject(ERROR, "A network error occurred.");
                    default:
                        promise.reject(ERROR, loginResult.getErrorData().getMessage());
                        break;
                }
            }
        }
    };

    // init line
    @ReactMethod
    public void init(String lineChannelID, final Promise promise){
        // channel id 받아서 초기화한다.
        this.lineChannelID = lineChannelID;

        try {
            currentPromise = promise;
            LineApiClient lineApiClient = getLineApiClient();
            promise.resolve(true);
        } catch (Exception e) {
            promise.reject("ERROR", e.toString());
        }
    }

    // login
    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @ReactMethod
    public void login(ReadableArray scopes, String botPrompt, final Promise promise){
        Log.d("scopes", scopes.toString());
        ArrayList<Object> _scopes = scopes.toArrayList();
        List<Scope> scopeList = new ArrayList<Scope>();
        for(Object scope:_scopes){
            if (scope.toString().equals("PROFILE")) {
                scopeList.add(Scope.PROFILE);
            }
            if (scope.toString().equals("OPENID")) {
                scopeList.add(Scope.OPENID_CONNECT);
            }
            if (scope.toString().equals("EMAIL")) {
                scopeList.add(Scope.OC_EMAIL);
            }
            if (scope.toString().equals("MESSAGE")) {
                scopeList.add(Scope.MESSAGE);
            }
            if (scope.toString().equals("BIRTHDATE")) {
                scopeList.add(Scope.OC_BIRTHDATE);
            }
            if (scope.toString().equals("REAL_NAME")) {
                scopeList.add(Scope.OC_REAL_NAME);
            }
            if (scope.toString().equals("FRIEND")) {
                scopeList.add(Scope.FRIEND);
            }
            if (scope.toString().equals("GROUP")) {
                scopeList.add(Scope.GROUP);
            }
            if (scope.toString().equals("ADDRESS")) {
                scopeList.add(Scope.OC_ADDRESS);
            }
            if (scope.toString().equals("GENDER")) {
                scopeList.add(Scope.OC_GENDER);
            }
            if (scope.toString().equals("PHONE_NUMBER")) {
                scopeList.add(Scope.OC_PHONE_NUMBER);
            }
        }
        Log.d("scopes2", scopeList.toString());
        try {
            currentPromise = promise;
            LineAuthenticationParams.BotPrompt mPrompt = null;
            if (botPrompt.equals("normal") || botPrompt.equals("aggressive")) {
                mPrompt = LineAuthenticationParams.BotPrompt.valueOf(botPrompt);
            }
            LineAuthenticationParams params = new LineAuthenticationParams.Builder().scopes(scopeList).botPrompt(mPrompt).build();
            Intent loginIntent = LineLoginApi.getLoginIntent(
                    this.reactContext,
                    this.lineChannelID,
                    params);

            Objects.requireNonNull(getCurrentActivity()).startActivityForResult(loginIntent, REQUEST_CODE);
        } catch (Exception e) {
            promise.reject("ERROR", e.toString());
        }
    }

    // Logout
    @ReactMethod
    public void logout(Promise promise) {
        LineApiResponse lineApiResponse = getLineApiClient().logout();
        if (lineApiResponse.isSuccess()) {
            WritableMap response = Arguments.createMap();
            response.putString("action", "LOGOUT");
            response.putString("code", lineApiResponse.getResponseCode().name());
            response.putNull("data");
            promise.resolve(response);
        } else {
            promise.reject(ERROR, lineApiResponse.getErrorData().getMessage());
        }
    }

    @ReactMethod
    public void getAccessToken(Promise promise) {
        LineApiResponse<LineAccessToken> lineApiResponse = getLineApiClient().getCurrentAccessToken();
        if (lineApiResponse.isSuccess()) {
            WritableMap response = Arguments.createMap();
            response.putString("action", "GET_ACCESS_TOKEN");
            response.putString("code", lineApiResponse.getResponseCode().name());
            response.putMap("data", parseAccessToken(lineApiResponse.getResponseData()));
            promise.resolve(response);
        } else {
            promise.reject(ERROR, lineApiResponse.getErrorData().getMessage());
        }
    }

    @ReactMethod
    public void getUserProfile(Promise promise) {
        LineApiResponse<LineProfile> lineApiResponse = getLineApiClient().getProfile();
        if (lineApiResponse.isSuccess()) {
            WritableMap response = Arguments.createMap();
            response.putString("action", "GET_USER_PROFILE");
            response.putString("code", lineApiResponse.getResponseCode().name());
            response.putMap("data", parseProfile(lineApiResponse.getResponseData()));
            promise.resolve(response);
        } else {
            promise.reject(ERROR, lineApiResponse.getErrorData().getMessage());
        }
    }

    @ReactMethod
    public void getFriendshipStatus(Promise promise) {
        LineApiResponse<LineFriendshipStatus> lineApiResponse = getLineApiClient().getFriendshipStatus();
        if (lineApiResponse.isSuccess()) {
            WritableMap response = Arguments.createMap();
            response.putString("action", "GET_FRIENDSHIP_STATUS");
            response.putString("code", lineApiResponse.getResponseCode().name());
            response.putBoolean("isFriend", lineApiResponse.getResponseData().isFriend());
            promise.resolve(response);
        } else {
            promise.reject(ERROR, lineApiResponse.getErrorData().getMessage());
        }
    }
}
