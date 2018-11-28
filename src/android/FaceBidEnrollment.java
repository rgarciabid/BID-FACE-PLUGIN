package com.teknei.bid;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Environment;
import android.util.Log;

import com.facetec.zoom.sdk.AuditTrailType;
import com.facetec.zoom.sdk.ZoomCustomization;
import com.facetec.zoom.sdk.ZoomSDK;
import com.facetec.zoom.sdk.ZoomVerificationActivity;
import com.facetec.zoom.sdk.ZoomVerificationResult;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public class FaceBidEnrollment extends CordovaPlugin {
    private ZoomCustomization customization;
    ArrayList<Bitmap> auditTrailResult;
    final int QUALITY_COMPRESION = 100;
    private byte[] photoBuffer;
    private File imageFile;
    private CallbackContext pendingCallbackContext = null;
    private static final String enrollmentEndPoint = "http://189.203.240.16:28080/bio-serv/";
    private static final String tokenEndPoint = "http://189.203.240.16:9999";
    private int timeOut = 60;
    private Integer responseStatus = 0;
    private boolean responseOk = false;
    private String errorMessage ="";
    private final String USR = "user";
    private final String PSW = "password";
    private OAuthAccessToken accessToken;
    private String bidAppToken ="";
    private String idTanTan = "";
    private String operationID = "";
    private boolean tokenSuccess = false;
    private String tokenError = "";
    private boolean enrollmentSuccess = false;
    private String enrollmentError = "";
    private final String grantType = "password";
    private long endTime;
    private File f;

    @Override
    public boolean execute(String action, JSONArray args, final CallbackContext callbackContext) throws JSONException {
        if ("initializeZoom".equals(action)) {
            initializeZoom(args,callbackContext);
        } else if ("enrollWithZoom".equals(action)){
            enrollWithZoom(callbackContext);
        }
        else {
            return false;
        }
        return true;
    }

    private void initializeZoom(JSONArray args, final CallbackContext callbackContext) throws JSONException{
        final String appToken = args.getString(0);
        idTanTan = args.getString(1);
        final Context context = this.cordova.getActivity().getApplicationContext();

        customization = new ZoomCustomization();
        customization.showZoomIntro = false;
        customization.showPreEnrollmentScreen = false;
        customization.showUserLockedScreen = false;
        customization.showSuccessScreen = false;
        customization.showFailureScreen = false;
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                ZoomSDK.setCustomization(customization);
                ZoomSDK.initialize(context, appToken, new ZoomSDK.InitializeCallback() {
                    @Override
                    public void onCompletion(boolean successful) {
                        if (successful) {
                            ZoomSDK.setAuditTrailType(AuditTrailType.HEIGHT_640);
                            enrollWithZoom(callbackContext);
                        }else{
                            callbackContext.error("Error al inicilizar Zoom, inténtelo de nuevo.");
                        }
                    }
                });
            }
        });
    }


    private void enrollWithZoom(final CallbackContext callbackContext){
        Intent enrollmentIntent = new Intent(this.cordova.getActivity(), ZoomVerificationActivity.class);
        enrollmentIntent.putExtra(ZoomSDK.EXTRA_RETRIEVE_ZOOM_BIOMETRIC, false);

        pendingCallbackContext = callbackContext;
        this.cordova.startActivityForResult(this, enrollmentIntent, ZoomSDK.REQUEST_CODE_VERIFICATION);
    }


    @Override
    public void onActivityResult (int requestCode, int resultCode, Intent data) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        f = new File(Environment.getExternalStorageDirectory() + "/.bid_img" + File.separator + "face_" + System.currentTimeMillis() + ".jpeg");
        if(resultCode == Activity.RESULT_OK) {

            if (requestCode == ZoomSDK.REQUEST_CODE_VERIFICATION) {

                ZoomVerificationResult zoomEnrollmentResult = data.getParcelableExtra(ZoomSDK.EXTRA_VERIFY_RESULTS);

                if (zoomEnrollmentResult.getFaceMetrics() != null && zoomEnrollmentResult.getFaceMetrics().getAuditTrail().size() > 0 ) {

                    auditTrailResult = zoomEnrollmentResult.getFaceMetrics().getAuditTrail();
                    Bitmap bitmap;

                    try {
                        int value = auditTrailResult.size();

                        bitmap = auditTrailResult.get(value-1);

                        bitmap.compress(Bitmap.CompressFormat.JPEG, QUALITY_COMPRESION, baos);

                        photoBuffer = baos.toByteArray();;

                    } catch (Exception e) {
                        deleteFile();
                        pendingCallbackContext.error("Falla al obtener fotografía, inténtelo de nuevo.");
                    }
                    deleteFile();
                    f = new File(Environment.getExternalStorageDirectory() + "/.bid_img" + File.separator + "face_" + System.currentTimeMillis() + ".jpeg");

                    try {
                        //write the bytes in file
                        FileOutputStream fo = new FileOutputStream(f);
                        fo.write(photoBuffer);
                        // remember close de FileOutput
                        fo.close();
                        imageFile = f;
                    } catch (IOException e) {
                        deleteFile();
                        pendingCallbackContext.error("Falla al procesar fotografía, inténtelo de nuevo.");
                    }

                } else {
                    deleteFile();
                    pendingCallbackContext.error("Falla al obtener métricas, inténtelo de nuevo.");
                }
            } else {
                deleteFile();
                pendingCallbackContext.error("Tipo de captura inválido, inténtelo de nuevo.");
            }
            //Captura de rostro exitosa
            if(imageFile == null || imageFile.length() == 0) {
                deleteFile();
                pendingCallbackContext.error("Imagen de rostro nula.");
            }else{
                getAccessToken();//Comienza enrolamiento.
            }
        } else {
            deleteFile();
            pendingCallbackContext.error("Falla al capturar fotografía, inténtelo de nuevo.");
            pendingCallbackContext = null;
        }
    }

    /*Lógica de enrolamiento en servidor biometrico*/
    private void faceFileSend(){
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try{
                    BIDEndPointServices api = new RetrofitSingleton().getInstance().build(enrollmentEndPoint,timeOut).create(BIDEndPointServices.class);

                    MultipartBody.Part jsonID =
                            MultipartBody.Part.createFormData("json", "operationId",
                                    RequestBody.create(MediaType.parse("application/json"), operationID));
                    MultipartBody.Part jsonFront =
                            MultipartBody.Part.createFormData("file", imageFile.getName(),
                                    RequestBody.create (MediaType.parse("image/jpeg"), imageFile));
                    Log.e("faceFileSendBC" , "Token:"+bidAppToken);
                    Log.e("faceFileSendBC" , "operationId:"+idTanTan);
                    Log.e("faceFileSendBC" , "Image Lenght:"+imageFile.length());

                    Call<ResponseServicesBID> call = api.enrollmentFacialFace("Bearer " + bidAppToken, jsonID, jsonFront);

                    Response<ResponseServicesBID> response = call.execute();
                    responseStatus = response.code();
                    Log.e("FBE-faceFileSend","onResponse responseStatus:"+ responseStatus);

                    if (response.isSuccessful()) {
                        enrollmentSuccess = true;
                        Log.e("FBE-faceFileSend","onResponse success");
                    }else {
                        enrollmentError = "Falla al enrolar:"+ responseStatus;
                        enrollmentSuccess = false;
                        Log.e("FBE-faceFileSend","onResponse fail:"+ responseStatus);
                    }

                    if(!enrollmentSuccess){
                        deleteFile();
                        Log.e("FBE-ifEnrollmentSuccess",""+ enrollmentSuccess);
                        pendingCallbackContext.error(enrollmentError);
                    }else{
                        deleteFile();
                        pendingCallbackContext.success("Enrolamiento exitoso Usuario " + idTanTan);
                        pendingCallbackContext = null;
                    }
                }catch (IOException ex){
                    enrollmentError = "No se logró conectar al servidor de enrolamiento, verifique conexión.";
                    enrollmentSuccess = false;
                    Log.e("FBE-faceFileSend","Onfailure:");
                }
            }
        });
    }

    private void getAccessToken(){
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                BIDEndPointServices api = new RetrofitSingleton().getInstance().build(tokenEndPoint,timeOut).create(BIDEndPointServices.class);
                Call<OAuthAccessToken> call = api.getAccessTokenByPassword(grantType,USR, PSW);
                try{
                    Response<OAuthAccessToken> response  = call.execute();
                    if (response.isSuccessful()) {
                        accessToken = response.body();
                        bidAppToken = accessToken.getAccessToken();
                        tokenSuccess = true;
                        if(!bidAppToken.equals("")){
                            getIdTantan();
                            faceFileSend();
                        }else{
                            deleteFile();
                            pendingCallbackContext.error("Token vacío inválido");
                        }
                        Log.e("FBE-getAccessToken:","onResponse, success:" + bidAppToken);
                    } else {
                        tokenError = "Fallo al obtener token de autenticación.";
                        tokenSuccess = false;
                        Log.e("FBE-getAccessToken:","onResponse fail");
                    }
                    if(!tokenSuccess){
                        deleteFile();
                        Log.e("FBE-if(!tokenSuccess):",""+ tokenSuccess);
                        pendingCallbackContext.error(tokenError);
                    }
                }catch (IOException ex){
                    tokenError = "No se logró conectar al servidor de autenticación, verifique conexión.";
                    tokenSuccess = false;
                    Log.e("FBE-getAccessToken:","onFailure");
                }
            }
        });
    }

    private void deleteFile(){
        if (f.exists())
            f.delete();
    }

    private void getIdTantan(){
        if (idTanTan.equals(""))
            idTanTan = "" + System.currentTimeMillis();
            
        JSONObject jsonOpId = new JSONObject();
        try {
            jsonOpId.put("operationId", idTanTan);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        operationID = jsonOpId.toString();
    }

    private class RetrofitSingleton {
        private RetrofitSingleton instance;
        private RetrofitSingleton(){}

        public RetrofitSingleton getInstance() {
            if (instance == null)
                instance = new RetrofitSingleton();
            return instance;
        }

        private Retrofit build(String baseURL, int timeOut) {
            OkHttpClient httpClient = buildHttpClient(timeOut);

            Gson gson = new GsonBuilder()
                    .setLenient()
                    .create();

            return new Retrofit.Builder()
                    .baseUrl(baseURL)
                    .validateEagerly(true)
                    .addConverterFactory(GsonConverterFactory.create(gson))
                    .client(httpClient)
                    .build();
        }

        private OkHttpClient buildHttpClient(int timeOut) {
            return new OkHttpClient.Builder()
                    .connectTimeout(timeOut, TimeUnit.SECONDS)
                    .readTimeout   (timeOut, TimeUnit.SECONDS)
                    .writeTimeout  (timeOut, TimeUnit.SECONDS)
                    .build();
        }
    }

    private interface BIDEndPointServices {
        @Multipart
        @POST("rest/v1/enrollment/facial/face")
        Call<ResponseServicesBID> enrollmentFacialFace (@Header("Authorization") String bidToken,
                                                        @Part MultipartBody.Part jsonOpId,
                                                        @Part MultipartBody.Part jsonImgFile);

        @Headers({"Authorization: Basic dXNlcmFwcDpwYXNzd29yZA==",
                "Content-Type: application/x-www-form-urlencoded"})
        @POST("/uaa/oauth/token")
        Call<OAuthAccessToken> getAccessTokenByPassword(
                @Query("grant_type") String type,
                @Query("username") String username,
                @Query("password") String password
        );
    }

    private class ResponseServicesBID {

        @SerializedName("resultOK")
        private boolean resultOK;

        @SerializedName("errorMessage")
        private String errorMessage;

        public boolean isResultOK() {
            return resultOK;
        }

        public void setResultOK(boolean resultOK) {
            this.resultOK = resultOK;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

    }

    private class OAuthAccessToken {

        /**
         * Token de acceso para realizar peticiónes a recursos protegidos
         */
        @SerializedName("access_token")
        private String accessToken;

        /**
         * Tipo de token obtenido, normalmente del tipo Bearer
         */
        @SerializedName("token_type")
        private String tokenType;

        /**
         * Tiempo de expiración del token en milisegundos
         */
        @SerializedName("expires_in")
        private Long expiresIn;

        /**
         * Permisos del usuario en el API
         */
        @SerializedName("scope")
        private String scope;

        public String getAccessToken() {
            return accessToken;
        }
        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public String getTokenType() {
            return tokenType;
        }

        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }

        public Long getExpiresIn() {
            return expiresIn;
        }

        public void setExpiresIn(Long expiresIn) {
            this.expiresIn = expiresIn;
        }

        public String getScope() {
            return scope;
        }

        public void setScope(String scope) {
            this.scope = scope;
        }
    }
}
