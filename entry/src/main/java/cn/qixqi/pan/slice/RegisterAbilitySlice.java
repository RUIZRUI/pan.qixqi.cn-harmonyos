package cn.qixqi.pan.slice;

import cn.qixqi.pan.ResourceTable;
import cn.qixqi.pan.model.User;
import cn.qixqi.pan.model.UserBase;
import cn.qixqi.pan.util.ElementUtil;
import cn.qixqi.pan.util.HttpUtil;
import cn.qixqi.pan.util.Toast;
import com.alibaba.fastjson.JSON;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.*;
import ohos.agp.components.element.ShapeElement;
import ohos.agp.render.Paint;
import ohos.agp.utils.Color;
import ohos.agp.window.dialog.CommonDialog;
import ohos.app.dispatcher.task.TaskPriority;
import ohos.eventhandler.EventHandler;
import ohos.eventhandler.EventRunner;
import ohos.eventhandler.InnerEvent;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import okhttp3.Call;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;


public class RegisterAbilitySlice extends AbilitySlice {

    private static final HiLogLabel LOG_LABEL = new HiLogLabel(3, 0xD001100, RegisterAbilitySlice.class.getName());

    // private static final String VALID_PHONE_NUM = "19818965587";

    private static final int REGISTER_SUCCESS = 1000;
    private static final int REGISTER_FAIL = 1001;

    private static final String REGISTER_URL = "http://ali4.qixqi.cn:5555/api/user/v1/user";

    private ScrollView registerScroll;
    private Text validUname;
    private Text validPhoneNum;
    private Text validAuthCode;
    private Text validPassword;
    private Text validPasswordConfirm;
    private TextField unameText;
    private TextField phoneNumText;
    private TextField authCodeText;
    private TextField passwordText;
    private TextField passwordConfirmText;
    private Button authCodeBtn;
    private Button registerBtn;
    private Text loginText;
    private Text retrievePassText;
    private CommonDialog commonDialog;
    private CommonDialog registerDialog;

    @Override
    public void onStart(Intent intent){
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_register);

        this.getWindow().setStatusBarColor(ElementUtil.getColor(this, ResourceTable.Color_colorSubBackground));
        this.getWindow().setNavigationBarColor(ElementUtil.getColor(this, ResourceTable.Color_colorSubBackground));

        initView();
        initListener();
    }

    /**
     * ?????????????????????????????????
     */
    private void initView(){
        registerScroll = (ScrollView) findComponentById(ResourceTable.Id_registerScroll);
        validUname = (Text) findComponentById(ResourceTable.Id_validUname);
        validPhoneNum = (Text) findComponentById(ResourceTable.Id_validPhoneNum);
        validAuthCode = (Text) findComponentById(ResourceTable.Id_validAuthCode);
        validPassword = (Text) findComponentById(ResourceTable.Id_validPassword);
        validPasswordConfirm = (Text) findComponentById(ResourceTable.Id_validPasswordConfirm);
        unameText = (TextField) findComponentById(ResourceTable.Id_unameText);
        phoneNumText = (TextField) findComponentById(ResourceTable.Id_phoneNumText);
        authCodeText = (TextField) findComponentById(ResourceTable.Id_authCodeText);
        passwordText = (TextField) findComponentById(ResourceTable.Id_passwordText);
        passwordConfirmText = (TextField) findComponentById(ResourceTable.Id_passwordConfirmText);
        authCodeBtn = (Button) findComponentById(ResourceTable.Id_authCodeBtn);
        registerBtn = (Button) findComponentById(ResourceTable.Id_registerBtn);
        loginText = (Text) findComponentById(ResourceTable.Id_toLogin);
        retrievePassText = (Text) findComponentById(ResourceTable.Id_toRetrievePass);
    }

    /**
     * ???????????????????????????
     */
    private void initListener(){
        // unameText ??????????????????
        unameText.setFocusChangedListener((component, isFocused) -> {
            if (isFocused) {
                // ????????????
                validUname.setVisibility(Component.HIDE);
            } else {
                // ????????????
                if (!unameValid(unameText.getText())){
                    validUname.setVisibility(Component.VISIBLE);
                }
            }
        });
        // phoneNumText ??????????????????
        phoneNumText.setFocusChangedListener((component, isFocused) -> {
            if (isFocused) {
                // ????????????
                validPhoneNum.setVisibility(Component.HIDE);
            } else {
                // ????????????
                if (!phoneNumValid(phoneNumText.getText())){
                    validPhoneNum.setVisibility(Component.VISIBLE);
                }
            }
        });
        // authCodeText ??????????????????
        authCodeText.setFocusChangedListener((component, isFocused) -> {
            if (isFocused){
                // ????????????
                validAuthCode.setVisibility(Component.HIDE);
            } else {
                // ????????????
                if (!authCodeValid(authCodeText.getText())){
                    validAuthCode.setVisibility(Component.VISIBLE);
                }
            }
        });

        // passwordText ??????????????????
        passwordText.setFocusChangedListener((component, isFocused) -> {
            if (isFocused){
                // ????????????
                validPassword.setVisibility(Component.HIDE);
                validPasswordConfirm.setVisibility(Component.HIDE);
            } else {
                // ????????????
                if (!passwordValid(passwordText.getText())){
                    validPassword.setVisibility(Component.VISIBLE);
                }
                if (!passwordConfirmValid(passwordText.getText(), passwordConfirmText.getText())){
                    validPasswordConfirm.setVisibility(Component.VISIBLE);
                }
            }
        });

        // passwordConfirmText ??????????????????
        passwordConfirmText.setFocusChangedListener((component, isFocused) -> {
            if (isFocused){
                // ????????????
                validPasswordConfirm.setVisibility(Component.HIDE);
            } else {
                // ????????????
                if (!passwordConfirmValid(passwordText.getText(), passwordConfirmText.getText())){
                    validPasswordConfirm.setVisibility(Component.VISIBLE);
                }
            }
        });

        // authCodeBtn ????????????
        authCodeBtn.setClickedListener(component -> {
            Toast.makeToast(RegisterAbilitySlice.this, getString(ResourceTable.String_clickedAuthCodeBtn),
                    Toast.TOAST_SHORT).show();
        });

        // registerBtn ????????????
        registerBtn.setClickedListener(component -> register(
                unameText.getText(), phoneNumText.getText(), authCodeText.getText(),
                passwordText.getText(), passwordConfirmText.getText()));

        // loginText ????????????
        loginText.setClickedListener(component -> {
            present(new LoginAbilitySlice(), new Intent());
        });

        // retrievePassText ????????????
        retrievePassText.setClickedListener(component -> {
            present(new RetrievePassAbilitySlice(), new Intent());
        });
    }


    /**
     * ?????? uname ??????
     * @param uname
     * @return
     */
    private boolean unameValid(String uname){
        if (uname != null && !uname.isEmpty()){
            return true;
        }
        return false;
    }

    /**
     * ?????? phoneNum ??????
     * @param phoneNum???11???
     * @return
     */
    private boolean phoneNumValid(String phoneNum){
        return phoneNum.matches("^((13[0-9])|(14[0|5|6|7|9])|(15[0-3])|(15[5-9])|(16[6|7])|(17[2|3|5|6|7|8])|(18[0-9])|(19[1|8|9]))\\d{8}$");
    }

    /**
     * ?????? authCode ??????
     * @param authCode???????????????
     * @return
     */
    private boolean authCodeValid(String authCode){
        return authCode.matches("^[0-9]{6}$");
    }

    /**
     * ??????????????????
     * @param password
     * @return
     */
    private boolean passwordValid(String password){
        return password.length() >= 6;
    }

    /**
     * ??????????????????????????????
     * @param password
     * @param passwordConfirm
     * @return
     */
    private boolean passwordConfirmValid(String password, String passwordConfirm){
        return password.equals(passwordConfirm);
    }

    /**
     * registerBtn ???????????????
     * ???????????????uname, phoneNum, authCode, password, passwordConfirm ??????
     * ????????????????????????????????????
     * ??????????????????????????????????????????
     * ?????????????????????????????????????????????
     * @param uname
     * @param phoneNum
     * @param authCode
     * @param password
     * @param passwordConfirm
     */
    private void register(final String uname, final String phoneNum, final String authCode,
                          final String password, final String passwordConfirm){
        if (!unameValid(unameText.getText())){
            validUname.setVisibility(Component.VISIBLE);
        } else if (!phoneNumValid(phoneNumText.getText())){
            validPhoneNum.setVisibility(Component.VISIBLE);
        } else if (!authCodeValid(authCodeText.getText())){
            validAuthCode.setVisibility(Component.VISIBLE);
        } else if (!passwordValid(passwordText.getText())){
            validPassword.setVisibility(Component.VISIBLE);
        } else if (!passwordConfirmValid(passwordText.getText(), passwordConfirmText.getText())){
            validPasswordConfirm.setVisibility(Component.VISIBLE);
        } else {
            // ???????????????????????????
            showProgress(true);
            // ?????????????????????????????????
            /* getGlobalTaskDispatcher(TaskPriority.DEFAULT)
                    .asyncDispatch( () -> {
                        try {
                            Thread.sleep(2000);
                        } catch (InterruptedException e){
                            Logger.getLogger(ElementUtil.class.getName()).log(Level.SEVERE, e.getMessage());
                        }

                        // ????????????????????????????????????
                        if (phoneNum.equals(VALID_PHONE_NUM)){
                            registerEventHandler.sendEvent(REGISTER_SUCCESS);
                        } else {
                            registerEventHandler.sendEvent(REGISTER_FAIL);
                        }
                    }); */
            // ???????????????????????????
            UserBase userBase = new UserBase()
                    .withUname(uname)
                    .withPhoneNumber(phoneNum)
                    .withPassword(password);
            String userBaseJson = JSON.toJSONString(userBase);
            HiLog.debug(LOG_LABEL, userBaseJson);

            RequestBody requestBody = RequestBody.create(HttpUtil.JSON, userBaseJson);
            HttpUtil.post(REGISTER_URL, requestBody, null, null, new okhttp3.Callback(){
                @Override
                public void onFailure(@NotNull Call call, @NotNull IOException e){
                    HiLog.error(LOG_LABEL, e.getMessage());
                    registerEventHandler.sendEvent(REGISTER_FAIL);
                }

                @Override
                public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                    String responseStr = response.body().string();
                    if (response.isSuccessful()){
                        HiLog.debug(LOG_LABEL, responseStr);
                        User user = JSON.parseObject(responseStr, User.class);
                        HiLog.debug(LOG_LABEL, user.toString());
                        registerEventHandler.sendEvent(REGISTER_SUCCESS);
                    } else {
                        HiLog.error(LOG_LABEL, responseStr);
                        registerEventHandler.sendEvent(REGISTER_FAIL);
                    }
                }
            });
        }
    }

    /**
     * ????????? registerBtn ??????????????????????????????
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
    private final EventHandler registerEventHandler =
            new EventHandler(EventRunner.getMainEventRunner()) {
                @Override
                protected void processEvent(InnerEvent event){
                    super.processEvent(event);
                    // ???????????????????????????
                    showProgress(false);
                    switch (event.eventId) {
                        case REGISTER_SUCCESS:
                            showRegisterDialog(true);
                            getGlobalTaskDispatcher(TaskPriority.DEFAULT)
                                    .delayDispatch( () -> {
                                        startLoginSlice();
                                    }, 2000);
                            break;
                        case REGISTER_FAIL:
                            showRegisterDialog(false);
                            break;
                        default:
                            break;
                    }
                }
            };

    /**
     * ???????????????????????????????????????
     */
    private void startLoginSlice(){
        // ???????????????????????? registerDialog
        if (registerDialog != null){
            registerDialog.destroy();
            registerDialog = null;
        }

        Intent intent = new Intent();
        // ???????????????????????? Ability???????????????????????????
        // [TODO] ??????Ability??????????????????
        intent.setFlags(Intent.FLAG_ABILITY_CLEAR_MISSION | Intent.FLAG_ABILITY_NEW_MISSION);
        present(new LoginAbilitySlice(), intent);
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
    private void showRegisterDialog(boolean success){
        // ??????????????????
        registerDialog = new CommonDialog(this);
        // ???xml????????????????????????
        Component registerDialogComponent = LayoutScatter.getInstance(this)
                .parse(ResourceTable.Layout_auth_dialog, null, false);
        Text dialogText = (Text) registerDialogComponent.findComponentById(ResourceTable.Id_dialog_text);
        Text dialogSubText = (Text) registerDialogComponent.findComponentById(ResourceTable.Id_dialog_sub_text);

        if (success){
            dialogText.setText(ResourceTable.String_success);
            dialogSubText.setText(ResourceTable.String_registerSuccess);
        } else {
            dialogText.setText(ResourceTable.String_fail);
            dialogSubText.setText(ResourceTable.String_registerFail);
        }

        registerDialog
                .setContentCustomComponent(registerDialogComponent)
                .setTransparent(true)
                .setSize(AttrHelper.vp2px(300, this), DirectionalLayout.LayoutConfig.MATCH_CONTENT)
                .setAutoClosable(true);
        registerDialog.show();
    }




    @Override
    public void onActive(){
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent){
        super.onForeground(intent);
    }
}
