package com.example.newoutlookdemo;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.gson.JsonObject;
import com.microsoft.graph.authentication.IAuthenticationProvider;
import com.microsoft.graph.concurrency.ICallback;
import com.microsoft.graph.core.ClientException;
import com.microsoft.graph.http.IHttpRequest;
import com.microsoft.graph.models.extensions.Contact;
import com.microsoft.graph.models.extensions.Drive;
import com.microsoft.graph.models.extensions.IGraphServiceClient;
import com.microsoft.graph.requests.extensions.GraphServiceClient;
import com.microsoft.graph.requests.extensions.IContactCollectionPage;
import com.microsoft.identity.client.AuthenticationCallback;
import com.microsoft.identity.client.IAccount;
import com.microsoft.identity.client.IAuthenticationResult;
import com.microsoft.identity.client.IPublicClientApplication;
import com.microsoft.identity.client.ISingleAccountPublicClientApplication;
import com.microsoft.identity.client.PublicClientApplication;
import com.microsoft.identity.client.SilentAuthenticationCallback;
import com.microsoft.identity.client.exception.MsalException;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;

public class NewOutlookActivity extends AppCompatActivity {

    private final static String[] SCOPES = {"Contacts.Read"};
    /* Azure AD v2 Configs */
    final static String AUTHORITY = "https://login.microsoftonline.com/common";
    private ISingleAccountPublicClientApplication mSingleAccountApp;

    private static final String TAG = NewOutlookActivity.class.getSimpleName();

    /* UI & Debugging Variables */
    Button signInButton;
    Button signOutButton;
    Button callGraphApiInteractiveButton;
    Button callGraphApiSilentButton;
    TextView logTextView;
    TextView currentUserTextView;
    private ArrayList<Contact> contactArrayList = new ArrayList<>();

    public static void start(Context context) {
        Intent intent = new Intent(context, NewOutlookActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();

       /* try {
            PackageInfo info = getPackageManager().getPackageInfo("com.example.newoutlookdemo", PackageManager.GET_SIGNATURES);
            for (Signature signature : info.signatures) {
                MessageDigest md;
                md = MessageDigest.getInstance("SHA");
                md.update(signature.toByteArray());
                String something = new String(Base64.encode(md.digest(), 0));
                //String something = new String(Base64.encodeBytes(md.digest()));
                Log.e("hash key", something);
            }
        } catch (PackageManager.NameNotFoundException e1) {
            Log.e("name not found", e1.toString());
        } catch (NoSuchAlgorithmException e) {
            Log.e("no such an algorithm", e.toString());
        } catch (Exception e) {
            Log.e("exception", e.toString());
        }*/

        PublicClientApplication.createSingleAccountPublicClientApplication(getApplicationContext(),
                R.raw.auth_configbn_single_account, new IPublicClientApplication.ISingleAccountApplicationCreatedListener() {
                    @Override
                    public void onCreated(ISingleAccountPublicClientApplication application) {
                        mSingleAccountApp = application;
                        loadAccount();
                    }

                    @Override
                    public void onError(MsalException exception) {
                        displayError(exception);
                    }
                });
    }


    //When app comes to the foreground, load existing account to determine if user is signed in
    private void loadAccount() {
        if (mSingleAccountApp == null) {
            return;
        }

        mSingleAccountApp.getCurrentAccountAsync(new ISingleAccountPublicClientApplication.CurrentAccountCallback() {
            @Override
            public void onAccountLoaded(@Nullable IAccount activeAccount) {
                // You can use the account data to update your UI or your app database.
                updateUI(activeAccount);
            }

            @Override
            public void onAccountChanged(@Nullable IAccount priorAccount, @Nullable IAccount currentAccount) {
                if (currentAccount == null) {
                    // Perform a cleanup task as the signed-in account changed.
                    performOperationOnSignOut();
                }
            }

            @Override
            public void onError(@NonNull MsalException exception) {
                displayError(exception);
            }
        });
    }

    private void initializeUI() {
        signInButton = findViewById(R.id.signIn);
        callGraphApiSilentButton = findViewById(R.id.callGraphSilent);
        callGraphApiInteractiveButton = findViewById(R.id.callGraphInteractive);
        signOutButton = findViewById(R.id.clearCache);
        logTextView = findViewById(R.id.txt_log);
        currentUserTextView = findViewById(R.id.current_user);

        //Sign in user
        signInButton.setOnClickListener(v -> {
            if (mSingleAccountApp == null) {
                return;
            }
            mSingleAccountApp.signIn(NewOutlookActivity.this, null, SCOPES, getAuthInteractiveCallback());
        });

        //Sign out user
        signOutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSingleAccountApp == null) {
                    return;
                }
                mSingleAccountApp.signOut(new ISingleAccountPublicClientApplication.SignOutCallback() {
                    @Override
                    public void onSignOut() {
                        updateUI(null);
                        performOperationOnSignOut();
                    }

                    @Override
                    public void onError(@NonNull MsalException exception) {
                        displayError(exception);
                    }
                });
            }
        });

        //Interactive
        callGraphApiInteractiveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSingleAccountApp == null) {
                    return;
                }
                mSingleAccountApp.acquireToken(NewOutlookActivity.this, SCOPES, getAuthInteractiveCallback());
            }
        });

        //Silent
        callGraphApiSilentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mSingleAccountApp == null) {
                    return;
                }
                mSingleAccountApp.acquireTokenSilentAsync(SCOPES, AUTHORITY, getAuthSilentCallback());
            }
        });
    }


    private AuthenticationCallback getAuthInteractiveCallback() {
        return new AuthenticationCallback() {
            @Override
            public void onSuccess(IAuthenticationResult authenticationResult) {
                /* Successfully got a token, use it to call a protected resource - MSGraph */
                Log.d(NewOutlookActivity.class.getName(), "Successfully authenticated");
                /* Update UI */
                updateUI(authenticationResult.getAccount());
                /* call graph */
                callGraphAPI(authenticationResult);
            }

            @Override
            public void onError(MsalException exception) {
                /* Failed to acquireToken */
                Log.d(NewOutlookActivity.class.getName(), "Authentication failed: " + exception.toString());
                displayError(exception);
            }

            @Override
            public void onCancel() {
                /* User canceled the authentication */
                Log.d(NewOutlookActivity.class.getName(), "User cancelled login.");
            }
        };
    }


    private SilentAuthenticationCallback getAuthSilentCallback() {
        return new SilentAuthenticationCallback() {
            @Override
            public void onSuccess(IAuthenticationResult authenticationResult) {
                Log.d(NewOutlookActivity.class.getName(), "Successfully authenticated");
                callGraphAPI(authenticationResult);
            }

            @Override
            public void onError(MsalException exception) {
                Log.d(NewOutlookActivity.class.getName(), "Authentication failed: " + exception.toString());
                displayError(exception);
            }
        };
    }


    private void callGraphAPI(IAuthenticationResult authenticationResult) {

        final String accessToken = authenticationResult.getAccessToken();

        IGraphServiceClient graphClient =
                GraphServiceClient
                        .builder()
                        .authenticationProvider(request -> {
                            Log.d(NewOutlookActivity.class.getName(), "Authenticating request," + request.getRequestUrl());
                            request.addHeader("Authorization", "Bearer " + accessToken);
                        })
                        .buildClient();


        // IGraphServiceClient graphClient = GraphServiceClient.builder().authenticationProvider( authProvider ).buildClient();
       /* Contact contact = (Contact) graphClient.me().contacts()
                .buildRequest()
                .get();
        */

    /*    graphClient
                .me()
                .contacts()
                .buildRequest()
                .get(new ICallback<IContactCollectionPage>() {
                    @Override
                    public void success(IContactCollectionPage iContactCollectionPage) {
                        Log.d(NewOutlookActivity.class.getName(), "Found Contact " + iContactCollectionPage);
                    }
                    @Override
                    public void failure(ClientException ex) {

                    }
                });

*/
        graphClient
                .me()
                .contacts()
                .buildRequest()
                .get(new ICallback<IContactCollectionPage>() {
                    @Override
                    public void success(IContactCollectionPage iContactCollectionPage) {
                        Log.d(TAG, "Found Drive " + iContactCollectionPage);
                        ArrayList<Contact>  contactArrayList = new ArrayList<>();
                        contactArrayList.addAll(iContactCollectionPage.getCurrentPage());
                        // displayGraphResult(iContactCollectionPage.getRawObject());
                        displayContactListData(contactArrayList);
                    }

                    @Override
                    public void failure(ClientException ex) {
                        displayError(ex);
                    }
                });
    }


    private void displayGraphResult(@NonNull final JsonObject graphResponse) {
        logTextView.setText(graphResponse.toString());
    }

    private void displayContactListData(ArrayList<Contact> contacts) {
        StringBuilder sb = new StringBuilder();
        for (Contact contact : contacts) {
            sb.append(contact.displayName);
            sb.append("\n");
        }

        logTextView.setText(sb.toString());
    }

    private void updateUI(@Nullable final IAccount account) {
        if (account != null) {
            signInButton.setEnabled(false);
            signOutButton.setEnabled(true);
            callGraphApiInteractiveButton.setEnabled(true);
            callGraphApiSilentButton.setEnabled(true);
            currentUserTextView.setText(account.getUsername());
        } else {
            signInButton.setEnabled(true);
            signOutButton.setEnabled(false);
            callGraphApiInteractiveButton.setEnabled(false);
            callGraphApiSilentButton.setEnabled(false);
            currentUserTextView.setText("");
            logTextView.setText("");
        }
    }

    private void displayError(@NonNull final Exception exception) {
        logTextView.setText(exception.toString());
    }

    private void performOperationOnSignOut() {
        final String signOutText = "Signed Out.";
        currentUserTextView.setText("");
        Toast.makeText(getApplicationContext(), signOutText, Toast.LENGTH_SHORT).show();
    }

}
