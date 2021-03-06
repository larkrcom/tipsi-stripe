package com.gettipsi.stripe;
 
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
 
import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableArray;
import com.gettipsi.stripe.dialog.AddCardDialogFragment;
import com.gettipsi.stripe.util.ArgCheck;
import com.gettipsi.stripe.util.Converters;
import com.gettipsi.stripe.util.Fun0;
import com.gettipsi.stripe.util.Utils;
import com.google.android.gms.wallet.WalletConstants;
import com.stripe.android.*;
import com.stripe.android.model.*;
 
import org.json.JSONArray;
import org.json.JSONException;
 
import java.util.List;
 
import static com.gettipsi.stripe.PayFlow.NO_CURRENT_ACTIVITY_MSG;
import static com.gettipsi.stripe.util.Converters.convertSourceToWritableMap;
import static com.gettipsi.stripe.util.Converters.convertTokenToWritableMap;
import static com.gettipsi.stripe.util.Converters.createBankAccount;
import static com.gettipsi.stripe.util.Converters.createCard;
import static com.gettipsi.stripe.util.Converters.getStringOrNull;
import static com.gettipsi.stripe.util.InitializationOptions.ANDROID_PAY_MODE_KEY;
import static com.gettipsi.stripe.util.InitializationOptions.ANDROID_PAY_MODE_PRODUCTION;
import static com.gettipsi.stripe.util.InitializationOptions.PUBLISHABLE_KEY;
import static com.gettipsi.stripe.util.InitializationOptions.ANDROID_PAY_MODE_TEST;
 
public class StripeModule extends ReactContextBaseJavaModule {
 
    private static final String MODULE_NAME = StripeModule.class.getSimpleName();
    private static final String TAG = "### " + MODULE_NAME + ": ";
 
    private static StripeModule sInstance = null;
 
    public static StripeModule getInstance() {
        return sInstance;
    }
 
    public Stripe getStripe() {
        return mStripe;
    }
 
    @Nullable
    private Promise mCreateSourcePromise;
 
    @Nullable
    private Source mCreatedSource;
 
    private String mPublicKey;
    private Stripe mStripe;
    private PayFlow mPayFlow;
 
    private String ephemeralKeyEndpoint;
 
    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
 
        @Override
        public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
            boolean handled = getPayFlow().onActivityResult(activity, requestCode, resultCode, data);
            if (!handled) {
                super.onActivityResult(activity, requestCode, resultCode, data);
            }
        }
    };
 
 
    public StripeModule(ReactApplicationContext reactContext) {
        super(reactContext);
 
        // Add the listener for `onActivityResult`
        reactContext.addActivityEventListener(mActivityEventListener);
 
        sInstance = this;
    }
 
    @Override
    public String getName() {
        return MODULE_NAME;
    }
 
    @ReactMethod
    public void init(@NonNull ReadableMap options) {
        ArgCheck.nonNull(options);
 
        String newPubKey = Converters.getStringOrNull(options, PUBLISHABLE_KEY);
        String newAndroidPayMode = Converters.getStringOrNull(options, ANDROID_PAY_MODE_KEY);
 
        ephemeralKeyEndpoint = options.getString("ephemeralKeyEndpoint");
 
        if (newPubKey != null && !TextUtils.equals(newPubKey, mPublicKey)) {
            ArgCheck.notEmptyString(newPubKey);
 
            mPublicKey = newPubKey;
            mStripe = new Stripe(getReactApplicationContext(), mPublicKey);
            getPayFlow().setPublishableKey(mPublicKey);
            PaymentConfiguration.init(mPublicKey);
        }
 
        if (newAndroidPayMode != null) {
            ArgCheck.isTrue(ANDROID_PAY_MODE_TEST.equals(newAndroidPayMode) || ANDROID_PAY_MODE_PRODUCTION.equals(newAndroidPayMode));
 
            getPayFlow().setEnvironment(androidPayModeToEnvironment(newAndroidPayMode));
        }
    }
 
    private PayFlow getPayFlow() {
        if (mPayFlow == null) {
            mPayFlow = PayFlow.create(
                    new Fun0<Activity>() {
                        public Activity call() {
                            return getCurrentActivity();
                        }
                    }
            );
        }
 
        return mPayFlow;
    }
 
    private static int androidPayModeToEnvironment(@NonNull String androidPayMode) {
        ArgCheck.notEmptyString(androidPayMode);
        return ANDROID_PAY_MODE_TEST.equals(androidPayMode.toLowerCase()) ? WalletConstants.ENVIRONMENT_TEST : WalletConstants.ENVIRONMENT_PRODUCTION;
    }
 
    @ReactMethod
    public void deviceSupportsAndroidPay(final Promise promise) {
        getPayFlow().deviceSupportsAndroidPay(false, promise);
    }
 
    @ReactMethod
    public void canMakeAndroidPayPayments(final Promise promise) {
        getPayFlow().deviceSupportsAndroidPay(true, promise);
    }
 
    @ReactMethod
    public void createTokenWithCard(final ReadableMap cardData, final Promise promise) {
        try {
            ArgCheck.nonNull(mStripe);
            ArgCheck.notEmptyString(mPublicKey);
 
            mStripe.createToken(
                    createCard(cardData),
                    mPublicKey,
                    new TokenCallback() {
                        public void onSuccess(Token token) {
                            promise.resolve(convertTokenToWritableMap(token));
                        }
 
                        public void onError(Exception error) {
                            error.printStackTrace();
                            promise.reject(TAG, error.getMessage());
                        }
                    });
        } catch (Exception e) {
            promise.reject(TAG, e.getMessage());
        }
    }
 
    @ReactMethod
    public void updateServerInfo(final ReadableMap data) {
        CustomerSessionManager.getInstance().updateServerInfo(
                data.getString("authToken"),
                ephemeralKeyEndpoint);
    }
 
    @ReactMethod
    public void getCustomerSources(final Promise promise) {
        CustomerSession session = CustomerSession.getInstance();

        Customer cachedCustomer = session.getCachedCustomer();
        if (cachedCustomer != null) {
            try {
                promise.resolve(mapCustomerSources(cachedCustomer));
            } catch (Exception e) {
                promise.reject("EXCEPTION", "Failed parsing json data!");
                Log.e(getClass().getName(), "Failed parsing json array.", e);
            }
            return;
        }

        session.retrieveCurrentCustomer(new CustomerSession.CustomerRetrievalListener() {
            @Override
            public void onCustomerRetrieved(@NonNull Customer customer) {
                try {
                  promise.resolve(mapCustomerSources(customer));
                } catch (Exception e) {
                  promise.reject("EXCEPTION", "Failed parsing json data!");
                  Log.e(getClass().getName(), "Failed parsing json array.", e);
                }
            }

            @Override
            public void onError(int errorCode, @Nullable String errorMessage) {
                promise.reject(String.valueOf(errorCode), errorMessage);
            }
        });
    }

    private WritableArray mapCustomerSources(Customer customer) throws JSONException {
        List<CustomerSource> sources = customer.getSources();
        JSONArray array = new JSONArray();

        for (CustomerSource data : sources) {
            array.put(data.toJson());
        }

        return Utils.arrayToWritableMap(array);
    }
 
    @ReactMethod
    public void setDefaultSource(final ReadableMap data, final Promise promise) {
        Activity currentActivity = getCurrentActivity();
 
        CustomerSession.CustomerRetrievalListener listener =
                new CustomerSession.CustomerRetrievalListener() {
                    @Override
                    public void onCustomerRetrieved(@NonNull Customer customer) {
                        promise.resolve("ok");
                    }
 
                    @Override
                    public void onError(int errorCode, @Nullable String errorMessage) {
                        String displayedError = errorMessage == null ? "" : errorMessage;
                        promise.reject(String.valueOf(errorCode), displayedError);
                    }
                };
 
        String sourceId = data.getString("sourceId");
        String sourceType = data.getString("sourceType");
        CustomerSession.getInstance().setCustomerDefaultSource(
                currentActivity,
                sourceId,
                sourceType,
                listener);
    }
 
    @ReactMethod
    public void addCustomerSource(final ReadableMap data, final Promise promise) {
        Activity activity = getCurrentActivity();
 
        CustomerSession.SourceRetrievalListener listener =
                new CustomerSession.SourceRetrievalListener() {
                    @Override
                    public void onSourceRetrieved(@NonNull Source source) {
                        promise.resolve(convertSourceToWritableMap(source));
                    }
 
                    @Override
                    public void onError(int errorCode, @Nullable String errorMessage) {
                        String displayedError = errorMessage == null ? "" : errorMessage;
                        promise.reject(String.valueOf(errorCode), displayedError);
                    }
                };
 
        String sourceId = data.getString("sourceId");
        String sourceType = data.getString("sourceType");
        CustomerSession.getInstance().addCustomerSource(activity, sourceId, sourceType, listener);
    }
 
    @ReactMethod
    public void endCustomerSession() {
        CustomerSessionManager.getInstance().endSession();
    }
 
    @ReactMethod
    public void createTokenWithBankAccount(final ReadableMap accountData, final Promise promise) {
        try {
            ArgCheck.nonNull(mStripe);
            ArgCheck.notEmptyString(mPublicKey);
 
            mStripe.createBankAccountToken(
                    createBankAccount(accountData),
                    mPublicKey,
                    null,
                    new TokenCallback() {
                        public void onSuccess(Token token) {
                            promise.resolve(convertTokenToWritableMap(token));
                        }
 
                        public void onError(Exception error) {
                            error.printStackTrace();
                            promise.reject(TAG, error.getMessage());
                        }
                    });
        } catch (Exception e) {
            promise.reject(TAG, e.getMessage());
        }
    }
 
    @ReactMethod
    public void deleteCustomerSource(final ReadableMap data, final Promise promise) {
        Activity activity = getCurrentActivity();
        final CustomerSession session = CustomerSession.getInstance();
        final String sourceId = data.getString("sourceId");

        CustomerSession.SourceRetrievalListener listener =
                new CustomerSession.SourceRetrievalListener() {
                    @Override
                    public void onSourceRetrieved(@NonNull Source source) {
                        session.updateCurrentCustomer(new CustomerSession.CustomerRetrievalListener() {
                            @Override
                            public void onCustomerRetrieved(@NonNull Customer customer) {
                                List<CustomerSource> sources = customer.getSources();
                                JSONArray array = new JSONArray();

                                for (CustomerSource data : sources) {
                                    if (!data.getId().equals(sourceId))
                                        array.put(data.toJson());
                                }

                                try {
                                    promise.resolve(Utils.arrayToWritableMap(array));
                                } catch (Exception e) {
                                    promise.reject("EXCEPTION", "Failed parsing json data!");
                                    Log.e(getClass().getName(), "Failed parsing json array.", e);
                                }
                            }

                            @Override
                            public void onError(int errorCode, @Nullable String errorMessage) {
                                promise.reject(String.valueOf(errorCode), errorMessage);
                            }
                        });
                    }

                    @Override
                    public void onError(int errorCode, @Nullable String errorMessage) {
                        String displayedError = errorMessage == null ? "" : errorMessage;
                        promise.reject(String.valueOf(errorCode), displayedError);
                    }
                };

        session.deleteCustomerSource(activity, sourceId, listener);
    }
 
    @ReactMethod
    public void paymentRequestWithCardForm(ReadableMap unused, final Promise promise) {
        Activity currentActivity = getCurrentActivity();
        try {
            ArgCheck.nonNull(currentActivity);
            ArgCheck.notEmptyString(mPublicKey);
 
            final AddCardDialogFragment cardDialog = AddCardDialogFragment.newInstance(mPublicKey);
            cardDialog.setPromise(promise);
            cardDialog.show(currentActivity.getFragmentManager(), "AddNewCard");
        } catch (Exception e) {
            promise.reject(TAG, e.getMessage());
        }
    }
 
    @ReactMethod
    public void paymentRequestWithAndroidPay(final ReadableMap payParams, final Promise promise) {
        getPayFlow().paymentRequestWithAndroidPay(payParams, promise);
    }
 
    @ReactMethod
    public void createSourceWithParams(final ReadableMap options, final Promise promise) {
        String sourceType = options.getString("type");
        SourceParams sourceParams = null;
        switch (sourceType) {
            case "card":
                String cardNumber = options.getString("number");
                int month = options.getInt("expMonth");
                int year = options.getInt("expYear");
 
                String cvc = options.getString("cvc");
 
                Card card = new Card(cardNumber, month, year, cvc);
                card.setName(options.getString("name"));
                card.setAddressLine1(options.getString("addressLine1"));
                card.setAddressLine2(options.getString("addressLine2"));
                card.setAddressCity(options.getString("addressCity"));
                card.setAddressState(options.getString("addressState"));
                card.setAddressCountry(options.getString("addressCountry"));
                card.setAddressZip(options.getString("addressZip"));
 
                sourceParams = SourceParams.createCardParams(card);
                break;
            case "alipay":
                sourceParams = SourceParams.createAlipaySingleUseParams(
                        options.getInt("amount"),
                        options.getString("currency"),
                        getStringOrNull(options, "name"),
                        getStringOrNull(options, "email"),
                        options.getString("returnURL"));
                break;
            case "bancontact":
                sourceParams = SourceParams.createBancontactParams(
                        options.getInt("amount"),
                        options.getString("name"),
                        options.getString("returnURL"),
                        getStringOrNull(options, "statementDescriptor"));
                break;
            case "giropay":
                sourceParams = SourceParams.createGiropayParams(
                        options.getInt("amount"),
                        options.getString("name"),
                        options.getString("returnURL"),
                        getStringOrNull(options, "statementDescriptor"));
                break;
            case "ideal":
                sourceParams = SourceParams.createIdealParams(
                        options.getInt("amount"),
                        options.getString("name"),
                        options.getString("returnURL"),
                        getStringOrNull(options, "statementDescriptor"),
                        getStringOrNull(options, "bank"));
                break;
            case "sepaDebit":
                sourceParams = SourceParams.createSepaDebitParams(
                        options.getString("name"),
                        options.getString("iban"),
                        getStringOrNull(options, "addressLine1"),
                        options.getString("city"),
                        options.getString("postalCode"),
                        options.getString("country"));
                break;
            case "sofort":
                sourceParams = SourceParams.createSofortParams(
                        options.getInt("amount"),
                        options.getString("returnURL"),
                        options.getString("country"),
                        getStringOrNull(options, "statementDescriptor"));
                break;
            case "threeDSecure":
                sourceParams = SourceParams.createThreeDSecureParams(
                        options.getInt("amount"),
                        options.getString("currency"),
                        options.getString("returnURL"),
                        options.getString("card"));
                break;
        }
 
        ArgCheck.nonNull(sourceParams);
 
        mStripe.createSource(sourceParams, new SourceCallback() {
            @Override
            public void onError(Exception error) {
                promise.reject(error);
            }
 
            @Override
            public void onSuccess(Source source) {
                if (Source.REDIRECT.equals(source.getFlow())) {
                    Activity currentActivity = getCurrentActivity();
                    if (currentActivity == null) {
                        promise.reject(TAG, NO_CURRENT_ACTIVITY_MSG);
                    } else {
                        mCreateSourcePromise = promise;
                        mCreatedSource = source;
                        String redirectUrl = source.getRedirect().getUrl();
                        Intent browserIntent = new Intent(currentActivity, OpenBrowserActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                .putExtra(OpenBrowserActivity.EXTRA_URL, redirectUrl);
                        currentActivity.startActivity(browserIntent);
                    }
                } else {
                    promise.resolve(convertSourceToWritableMap(source));
                }
            }
        });
    }
 
    void processRedirect(@Nullable Uri redirectData) {
        if (mCreatedSource == null || mCreateSourcePromise == null) {
 
            return;
        }
 
        if (redirectData == null) {
 
            mCreateSourcePromise.reject(TAG, "Cancelled");
            mCreatedSource = null;
            mCreateSourcePromise = null;
            return;
        }
 
        final String clientSecret = redirectData.getQueryParameter("client_secret");
        if (!mCreatedSource.getClientSecret().equals(clientSecret)) {
            mCreateSourcePromise.reject(TAG, "Received redirect uri but there is no source to process");
            mCreatedSource = null;
            mCreateSourcePromise = null;
            return;
        }
 
        final String sourceId = redirectData.getQueryParameter("source");
        if (!mCreatedSource.getId().equals(sourceId)) {
            mCreateSourcePromise.reject(TAG, "Received wrong source id in redirect uri");
            mCreatedSource = null;
            mCreateSourcePromise = null;
            return;
        }
 
        final Promise promise = mCreateSourcePromise;
 
        // Nulls those properties to avoid processing them twice
        mCreatedSource = null;
        mCreateSourcePromise = null;
 
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                Source source = null;
                try {
                    source = mStripe.retrieveSourceSynchronous(sourceId, clientSecret);
                } catch (Exception e) {
 
                    return null;
                }
 
                switch (source.getStatus()) {
                    case Source.CHARGEABLE:
                    case Source.CONSUMED:
                        promise.resolve(convertSourceToWritableMap(source));
                        break;
                    case Source.CANCELED:
                        promise.reject(TAG, "User cancelled source redirect");
                        break;
                    case Source.PENDING:
                    case Source.FAILED:
                    case Source.UNKNOWN:
                        promise.reject(TAG, "Source redirect failed");
                }
                return null;
            }
        }.execute();
    }
}