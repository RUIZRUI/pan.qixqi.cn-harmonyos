package cn.qixqi.pan.slice;

import cn.qixqi.pan.MyApplication;
import cn.qixqi.pan.ResourceTable;
import cn.qixqi.pan.dao.TokenDao;
import cn.qixqi.pan.dao.UserDao;
import cn.qixqi.pan.dao.impl.TokenDaoImpl;
import cn.qixqi.pan.dao.impl.UserDaoImpl;
import cn.qixqi.pan.model.Token;
import cn.qixqi.pan.model.User;
import cn.qixqi.pan.util.ElementUtil;
import cn.qixqi.pan.util.HttpUtil;
import cn.qixqi.pan.util.Toast;
import com.alibaba.fastjson.JSON;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.aafwk.content.Operation;
import ohos.agp.components.*;
import ohos.agp.components.element.ShapeElement;
import ohos.agp.render.Paint;
import ohos.agp.utils.Color;
import ohos.agp.window.dialog.CommonDialog;
import ohos.app.dispatcher.TaskDispatcher;
import ohos.app.dispatcher.task.TaskPriority;
import ohos.eventhandler.EventHandler;
import ohos.eventhandler.EventRunner;
import ohos.eventhandler.InnerEvent;
import ohos.global.icu.text.TimeZoneNames;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoginAbilitySlice extends AbilitySlice {

    private static final HiLogLabel LOG_LABEL = new HiLogLabel(3, 0xD001100, LoginAbilitySlice.class.getName());
    private TokenDao tokenDao;
    private UserDao userDao;

    // private static final String VALID_MAIL = "123@163.com";
    private static final int LOGIN_SUCCESS = 1000;
    private static final int LOGIN_FAIL = 1001;
    private static final String LOGIN_URL = "http://ali4.qixqi.cn:5555/api/auth/auth/oauth/token";
    private static final String GET_USER_URL = "http://ali4.qixqi.cn:5555/api/user/v1/user/";
    private String AUTHORIZATION;

    private ScrollView loginScroll;
    private Text validAuthInfo;
    private Text validPassword;
    private TextField authInfoText;
    private TextField passwordText;
    private Button loginBtn;
    private Text registerText;
    private Text retrievePassText;
    private CommonDialog commonDialog;
    private CommonDialog loginDialog;

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_login);

        this.getWindow().setStatusBarColor(ElementUtil.getColor(this, ResourceTable.Color_colorSubBackground));
        this.getWindow().setNavigationBarColor(ElementUtil.getColor(this, ResourceTable.Color_colorSubBackground));

        tokenDao = new TokenDaoImpl(MyApplication.getAppContext());
        // tokenDao.exist();
        userDao = new UserDaoImpl(MyApplication.getAppContext());

        initAuthorization();
        initView();
        initListener();
    }

    /**
     * ????????? AUTHORIZATION
     */
    private void initAuthorization(){
        String encodeInfo = Base64.getEncoder().encodeToString(String.format("%s:%s",
                getString(ResourceTable.String_client_username),
                getString(ResourceTable.String_client_password)).getBytes());
        // Basic ??????
        AUTHORIZATION = String.format("Basic %s", encodeInfo);
    }

    /**
     * ?????????????????????????????????
     */
    private void initView(){
        loginScroll = (ScrollView) findComponentById(ResourceTable.Id_loginScroll);
        authInfoText = (TextField) findComponentById(ResourceTable.Id_authInfoText);
        passwordText = (TextField) findComponentById(ResourceTable.Id_passwordText);
        validAuthInfo = (Text) findComponentById(ResourceTable.Id_validAuthInfo);
        validPassword = (Text) findComponentById(ResourceTable.Id_validPassword);
        loginBtn = (Button) findComponentById(ResourceTable.Id_loginBtn);
        loginBtn.setEnabled(false);
        registerText = (Text) findComponentById(ResourceTable.Id_toRegister);
        retrievePassText = (Text) findComponentById(ResourceTable.Id_toRetrievePass);
    }

    /**
     * ???????????????????????????
     */
    private void initListener(){
        authInfoText.addTextObserver(
                // authInfoText ?????????????????????
                (text, var, i1, i2) -> {
                    validAuthInfo.setVisibility(Component.HIDE);
                    validPassword.setVisibility(Component.HIDE);
                });
        // ?????????????????? onPassTextUpdated ??????????????????????????????
        passwordText.addTextObserver(this::onPassTextUpdated);
        loginBtn.setClickedListener(component -> login(
                authInfoText.getText(), passwordText.getText()
        ));
        /* registerText.setClickedListener(component ->
                Toast.makeToast(LoginAbilitySlice.this,
                        getString(ResourceTable.String_clickedRegister),
                        Toast.TOAST_SHORT).show()); */
        registerText.setClickedListener(component -> present(new RegisterAbilitySlice(), new Intent()));
        /* retrievePassText.setClickedListener(component ->
                Toast.makeToast(LoginAbilitySlice.this,
                        getString(ResourceTable.String_clickedRetrievePass),
                        Toast.TOAST_SHORT).show()); */
        retrievePassText.setClickedListener(component -> present(new RetrievePassAbilitySlice(), new Intent()));
    }

    /**
     * ??????passwordText?????????????????????loginBtn??????
     * ???passwordText??????????????????validPassword?????????validAuthInfo??????
     * @param text: Text?????????text???????????????passwordText
     * @param var
     * @param i1
     * @param i2
     */
    private void onPassTextUpdated(String text, int var, int i1, int i2){
        if (text != null && !text.isEmpty()){
            // ??? passwordText ??????????????????
            loginBtn.setEnabled(true);
            loginBtn.setBackground(new ShapeElement(this, ResourceTable.Graphic_background_auth_btn_can));
        } else {
            // ??? passwordText ???????????????
            loginBtn.setEnabled(false);
            loginBtn.setBackground(new ShapeElement(this, ResourceTable.Graphic_background_auth_btn));
        }
        validAuthInfo.setVisibility(Component.HIDE);
        validPassword.setVisibility(Component.HIDE);
    }

    /**
     * ?????? authInfo ??????
     * @param authInfo??????????????????????????????????????????
     * @return
     */
    private boolean authInfoValid(String authInfo){
        if (authInfo != null && !authInfo.isEmpty()){
            // authInfo ?????????
            return true;
        }
        return false;
    }

    /**
     * ?????? password ??????
     * @param password
     * @return
     */
    private boolean passwordValid(String password){
        return password.length() >= 4;
    }

    /**
     * loginBtn ???????????????
     * ??????????????? authInfo ??? passowrd ??????
     * ????????????????????????????????????
     * ??????????????????????????????????????????
     * ?????????????????????????????????????????????
     * @param authInfo
     * @param password
     */
    private void login(final String authInfo, final String password){
        validAuthInfo.setVisibility(Component.HIDE);
        validPassword.setVisibility(Component.HIDE);
        if (!authInfoValid(authInfo)) {
            // authInfo ???????????????
            validAuthInfo.setVisibility(Component.VISIBLE);
        } else if (!passwordValid(password)) {
            // password ???????????????
            validPassword.setVisibility(Component.VISIBLE);
        } else {
            // ?????????????????????
            showProgress(true);
            // ?????????????????????????????????
            /* getGlobalTaskDispatcher(TaskPriority.DEFAULT)
                    .asyncDispatch(
                            () -> {
                               try {
                                   Thread.sleep(3000);
                               } catch (InterruptedException e) {
                                   Logger.getLogger(ElementUtil.class.getName()).log(Level.SEVERE, e.getMessage());
                               }

                               // ????????????????????????????????????
                                if (authInfo.equals(VALID_MAIL)) {
                                    loginEventHandler.sendEvent(LOGIN_SUCCESS);
                                } else {
                                    loginEventHandler.sendEvent(LOGIN_FAIL);
                                }
                            }); */
            // ???????????????????????????
            RequestBody requestBody = new FormBody.Builder()
                    .add("grant_type", getString(ResourceTable.String_client_grant_type))
                    .add("scope", getString(ResourceTable.String_client_scope))
                    .add("username", authInfo)
                    .add("password", password)
                    .build();
            Map<String, String> addHeaders = new HashMap<String, String>();
            addHeaders.put("Authorization", AUTHORIZATION);
            HttpUtil.post(LOGIN_URL, requestBody, null, addHeaders, new okhttp3.Callback(){
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e){
                    // showResponse(LOGIN_FAIL, e.getMessage());
                    HiLog.error(LOG_LABEL, e.getMessage());
                    loginEventHandler.sendEvent(LOGIN_FAIL);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                    // showResponse(LOGIN_SUCCESS, response.body().string().trim());
                    String responseStr = response.body().string();
                    if (response.isSuccessful()){
                        HiLog.debug(LOG_LABEL, responseStr);
                        // ????????????
                        Token token = JSON.parseObject(responseStr, Token.class);
                        tokenDao.save(token);
                        HiLog.debug(LOG_LABEL, tokenDao.get().toString());
                        // ??????????????????
                        getUserInfo(token.getAccessToken(), token.getUid());
                        // ????????????????????????
                        // loginEventHandler.sendEvent(LOGIN_SUCCESS);
                    } else {
                        HiLog.error(LOG_LABEL, responseStr);
                        loginEventHandler.sendEvent(LOGIN_FAIL);
                    }
                }
            });
        }
    }

    private void getUserInfo(String accessToken, String uid){
        if (accessToken == null || accessToken.isEmpty()){
            HiLog.error(LOG_LABEL,"accessToken is null or empty");
            loginEventHandler.sendEvent(LOGIN_FAIL);
        } else if (uid == null || uid.isEmpty()){
            HiLog.error(LOG_LABEL, "uid is null or empty");
            loginEventHandler.sendEvent(LOGIN_FAIL);
        } else {
            // ?????????????????????????????????
            Map<String, String> addHeaders = new HashMap<>();
            String Authorization = String.format("Bearer %s", accessToken);
            addHeaders.put("Authorization", Authorization);
            String url = String.format("%s%s", GET_USER_URL, uid);
            HttpUtil.get(url, null, addHeaders, new okhttp3.Callback(){
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e){
                    HiLog.error(LOG_LABEL, e.getMessage());
                    loginEventHandler.sendEvent(LOGIN_FAIL);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                    String responseStr = response.body().string();
                    if (response.isSuccessful()){
                        HiLog.debug(LOG_LABEL, responseStr);
                        // ???????????????????????????
                        User user = JSON.parseObject(responseStr, User.class);
                        HiLog.debug(LOG_LABEL, user.toString());
                        userDao.save(user);
                        HiLog.debug(LOG_LABEL, userDao.get().toString());
                        // ????????????????????????
                        loginEventHandler.sendEvent(LOGIN_SUCCESS);
                    } else {
                        HiLog.error(LOG_LABEL, responseStr);
                        loginEventHandler.sendEvent(LOGIN_FAIL);
                    }
                }
            });
        }
    }

    /**
     * ????????????(UI??????)?????????????????????????????????UI
     * @param msg
     */
    /* private void showResponse(final int resultCode, final String msg){
        TaskDispatcher uiTaskDispatcher = getUITaskDispatcher();
        uiTaskDispatcher.asyncDispatch(() -> {
            loginEventHandler.sendEvent(resultCode);
        });
    }*/

    /**
     * ????????? loginBtn ??????????????????????????????
     * @param show
     */
    private void showProgress(final boolean show){
        // ??? commonDialog ????????????????????? commonDialog
        if (commonDialog == null){
            commonDialog = new CommonDialog(this);

            // ??????????????????
            Component circleProgress = drawCircleProgress(AttrHelper.vp2px(6, this),
                    AttrHelper.vp2px(3, this));
            commonDialog
                    .setContentCustomComponent(circleProgress)
                    .setTransparent(true)
                    .setSize(DirectionalLayout.LayoutConfig.MATCH_CONTENT, DirectionalLayout.LayoutConfig.MATCH_CONTENT);
        }

        // ????????????????????????
        if (show){
            commonDialog.show();
        } else {
            commonDialog.destroy();
            commonDialog = null;
        }
    }

    // ????????? EventHandler?????????????????????
    private final EventHandler loginEventHandler =
            new EventHandler(EventRunner.getMainEventRunner()) {
                @Override
                protected void processEvent(InnerEvent event){
                    super.processEvent(event);
                    // ???????????????????????????
                    showProgress(false);
                    switch (event.eventId) {
                        case LOGIN_SUCCESS:
                            showLoginDialog(true);
                            getGlobalTaskDispatcher(TaskPriority.DEFAULT)
                                    .delayDispatch( () -> {
                                        startMainAbility();
                                    }, 2000);
                            break;
                        case LOGIN_FAIL:
                            showLoginDialog(false);
                            break;
                        default:
                            break;
                    }
                }
            };

    /**
     * ?????????????????????????????????
     */
    private void startMainAbility(){
        // ???????????????????????? loginDialog
        if (loginDialog != null){
            loginDialog.destroy();
            loginDialog = null;
        }

        Intent intent = new Intent();
        Operation operation = new Intent.OperationBuilder()
                .withDeviceId("")
                .withBundleName("cn.qixqi.pan")
                .withAbilityName("cn.qixqi.pan.MainAbility")
                .build();
        intent.setOperation(operation);
        // ???????????????????????? Ability???????????????????????????
        intent.setFlags(Intent.FLAG_ABILITY_CLEAR_MISSION | Intent.FLAG_ABILITY_NEW_MISSION);
        startAbility(intent);
    }

    // ??????????????????????????????????????????: 0~12
    private int roateNum = 0;

    /**
     * ??????????????????
     * @param maxRadius???????????????????????????
     * @param minRadius???????????????????????????
     * @return???????????????
     */
    private Component drawCircleProgress(int maxRadius, int minRadius){
        final int circleNum = 12;

        // ???????????????
        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL_STYLE);
        paint.setColor(Color.WHITE);

        // ???????????????
        Component circleProgress = new Component(this);
        circleProgress.setComponentSize(AttrHelper.vp2px(100, this), AttrHelper.vp2px(100, this));
        circleProgress.setBackground(new ShapeElement(this, ResourceTable.Graphic_auth_dialog_bg));

        // ????????????
        circleProgress.addDrawTask(
                (component, canvas) -> {
                    // ?????????????????????
                    if (roateNum == circleNum) {
                        roateNum = 0;
                    }

                    // ????????????
                    canvas.rotate(30 * roateNum, (float) (component.getWidth() / 2), (float) (component.getHeight() / 2));
                    roateNum ++;
                    int radius = (Math.min(component.getWidth(), component.getHeight())) / 2 - maxRadius;
                    float radiusIncrement = (float) (maxRadius - minRadius) / circleNum;
                    double angle = 2 * Math.PI / circleNum;

                    // ???????????????
                    for (int i = 0; i < circleNum; i++) {
                        float x = (float) (component.getWidth() / 2 + Math.cos(i * angle) * radius);
                        float y = (float) (component.getHeight() / 2 - Math.sin(i * angle) * radius);
                        paint.setAlpha((1 - (float) i / circleNum));
                        canvas.drawCircle(x, y, maxRadius - radiusIncrement * i, paint);
                    }

                    // ??????????????????
                    getUITaskDispatcher()
                            .delayDispatch(
                                    circleProgress::invalidate,
                                    150);
                });
        return circleProgress;
    }

    /**
     * ?????????????????????????????????
     * ?????????????????????????????????
     * @param success
     */
    private void showLoginDialog(boolean success){
        // ??????????????????
        loginDialog = new CommonDialog(this);
        // ???xml????????????????????????
        Component loginDialogComponent = LayoutScatter.getInstance(this)
                .parse(ResourceTable.Layout_auth_dialog, null, false);
        Text dialogText = (Text) loginDialogComponent.findComponentById(ResourceTable.Id_dialog_text);
        Text dialogSubText = (Text) loginDialogComponent.findComponentById(ResourceTable.Id_dialog_sub_text);

        if (success){
            dialogText.setText(ResourceTable.String_success);
            dialogSubText.setText(ResourceTable.String_loginSuccess);
        } else {
            dialogText.setText(ResourceTable.String_fail);
            dialogSubText.setText(ResourceTable.String_loginFail);
        }

        loginDialog
                .setContentCustomComponent(loginDialogComponent)
                .setTransparent(true)
                .setSize(AttrHelper.vp2px(300, this), DirectionalLayout.LayoutConfig.MATCH_CONTENT)
                .setAutoClosable(true);
        loginDialog.show();
    }

    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }
}
