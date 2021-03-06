package cn.qixqi.pan.slice;

import cn.qixqi.pan.MyApplication;
import cn.qixqi.pan.ResourceTable;
import cn.qixqi.pan.dao.TokenDao;
import cn.qixqi.pan.dao.impl.TokenDaoImpl;
import cn.qixqi.pan.datamodel.BottomBarItemInfo;
import cn.qixqi.pan.datamodel.FileItemInfo;
import cn.qixqi.pan.datamodel.FolderItemInfo;
import cn.qixqi.pan.filter.GifSizeFilter;
import cn.qixqi.pan.model.*;
import cn.qixqi.pan.model.File;
import cn.qixqi.pan.util.ElementUtil;
import cn.qixqi.pan.util.FastDFSUtil;
import cn.qixqi.pan.util.HttpUtil;
import cn.qixqi.pan.util.Toast;
import cn.qixqi.pan.view.BottomBarFSItemView;
import cn.qixqi.pan.view.BottomBarItemView;
import cn.qixqi.pan.view.FileItemView;
import cn.qixqi.pan.view.FolderItemView;
import cn.qixqi.pan.view.adapter.ChildItemProvider;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.zhihu.matisse.Matisse;
import com.zhihu.matisse.MatisseAbility;
import com.zhihu.matisse.MimeType;
import com.zhihu.matisse.filter.Filter;
import ohos.aafwk.ability.*;
import ohos.aafwk.content.Intent;
import ohos.aafwk.content.Operation;
import ohos.agp.components.*;
import ohos.agp.utils.Color;
import ohos.agp.utils.LayoutAlignment;
import ohos.agp.utils.TextAlignment;
import ohos.agp.window.dialog.CommonDialog;
import ohos.agp.window.dialog.IDialog;
import ohos.app.dispatcher.TaskDispatcher;
import ohos.app.dispatcher.task.TaskPriority;
import ohos.bundle.IBundleManager;
import ohos.data.dataability.DataAbilityPredicates;
import ohos.data.rdb.ValuesBucket;
import ohos.data.resultset.ResultSet;
import ohos.hiviewdfx.Debug;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import ohos.utils.net.Uri;
import okhttp3.Call;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.apache.commons.codec.digest.DigestUtils;
import org.csource.common.MyException;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.stream.IntStream;

import static ohos.agp.components.ComponentContainer.LayoutConfig.MATCH_CONTENT;
import static ohos.agp.components.ComponentContainer.LayoutConfig.MATCH_PARENT;

public class FileSystemAbilitySlice extends AbilitySlice implements ChildItemProvider.Callback{

    private static final HiLogLabel LOG_LABEL = new HiLogLabel(3, 0xD001100, FileSystemAbilitySlice.class.getName());
    private static final int MY_PERMISSIONS_REQUEST_CAMERA = 520;

    // ListContainer ????????????????????????
    private static final int OVER_SCROLL_PERCENT = 40;
    private static final float OVER_SCROLL_RATE = 0.6f;
    private static final int REMAIN_VISIBLE_PERCENT = 20;

    private static final int REQUEST_CODE_CHOOSE = 1;
    private List<Uri> mSelected;

    private TokenDao tokenDao;
    private FolderLink folderLink;
    private AbilitySlice abilitySlice;
    private DataAbilityHelper helper;
    private static final String UPLOAD_BASE_URI = "dataability:///cn.qixqi.pan.data.FileUploadDataAbility";
    private static final String DOWNLOAD_BASE_URI = "dataability:///cn.qixqi.pan.data.FileDownloadDataAbility";
    private static final String UPLOAD_DATA_PATH = "/fileUpload";
    private static final String DOWNLOAD_DATA_PATH = "/fileDownload";
    private String rootDir;

    private static final String GET_FOLDER_LINK_URL = "http://ali4.qixqi.cn:5555/api/filesystem/v1/filesystem/folderLink";
    private static final String GET_FILE_ID_URL = "http://ali4.qixqi.cn:5555/api/filesystem/v1/filesystem/fileMd5";
    private static final String ADD_FILE_URL = "http://ali4.qixqi.cn:5555/api/filesystem/v1/filesystem/file";
    private static final String ADD_FOLDER_CHILDREN = "http://ali4.qixqi.cn:5555/api/filesystem/v1/filesystem/folderLink/child/children";
    private static final String GET_FILE_URL = "http://ali4.qixqi.cn:5555/api/filesystem/v1/filesystem/file/%s/url";
    private static final String SHARE_FILE = "http://ali4.qixqi.cn:5555/api/filesharing/v1/filesharing/fileShare/generator";
    private static final String DELETE_FILE = "http://ali4.qixqi.cn:5555/api/filesystem/v1/filesystem/folderLink/child/children";
    private static final String RENAME_FILE = "http://ali4.qixqi.cn:5555/api/filesystem/v1/filesystem/folderLink/child/children";

    // private ListContainer foldersContainer;
    // private ListContainer filesContainer;
    // private FileItemProvider fileItemProvider;
    // private FolderItemProvider folderItemProvider;
    private ListContainer childrenContainer;
    private ChildItemProvider childItemProvider;
    private FolderChildren selectedChildren;
    private int selectedItemCount = 0;

    private Text title;
    private DirectionalLayout downloadItemLayout;
    private DirectionalLayout uploadItemLayout;

    private List<BottomBarItemInfo> bottomBarItemInfoList;
    // ???????????????????????????????????????????????????
    private List<BottomBarItemInfo> bottomBarFSItemInfoList;
    private Component bottomBar;
    private Component bottomBarFS;
    private boolean isItemSelected = false;     // ??????????????????????????????

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_file_system);

        abilitySlice = this;
        helper = DataAbilityHelper.creator(this);
        rootDir = getFilesDir() + java.io.File.separator + "Download" + java.io.File.separator;

        this.getWindow().setStatusBarColor(ElementUtil.getColor(this, ResourceTable.Color_colorSubBackground));
        this.getWindow().setNavigationBarColor(ElementUtil.getColor(this, ResourceTable.Color_colorSubBackground));

        tokenDao = new TokenDaoImpl(MyApplication.getAppContext());
        initView();
        initListener();

        // ?????????????????????
        setBottomToolBar();

        getFolderLink();

        // ??????????????????
        requestPermissions();
    }

    /**
     * ????????????????????????
     */
    private void initView(){
        // foldersContainer = (ListContainer) findComponentById(ResourceTable.Id_folders_container);
        // filesContainer = (ListContainer) findComponentById(ResourceTable.Id_files_container);
        childrenContainer = (ListContainer) findComponentById(ResourceTable.Id_children_container);
        // **********?????????*************
        title = (Text) findComponentById(ResourceTable.Id_title);
        title.setText(ResourceTable.String_file_title);
        downloadItemLayout = (DirectionalLayout) findComponentById(ResourceTable.Id_download_item_layout);
        uploadItemLayout = (DirectionalLayout) findComponentById(ResourceTable.Id_upload_item_layout);
        // **********???????????????************
        bottomBar = (DirectionalLayout) findComponentById(ResourceTable.Id_bottom_bar);
        bottomBarFS = (DirectionalLayout) findComponentById(ResourceTable.Id_bottom_bar_selected);
    }

    /**
     * ??????????????????
     */
    private void initListener(){
        // **********?????????*************
        // ????????????????????????
        title.setClickedListener( component -> {
            Toast.makeToast(abilitySlice, "????????????", Toast.TOAST_SHORT).show();
        });
        // downloadItemLayout ????????????
        downloadItemLayout.setClickedListener(component -> {
            // Toast.makeToast(abilitySlice, "??????downloadItemLayout", Toast.TOAST_SHORT).show();
            Intent intent = new Intent();
            Operation operation = new Intent.OperationBuilder()
                    .withDeviceId("")
                    .withBundleName("cn.qixqi.pan")
                    .withAbilityName("cn.qixqi.pan.FileHistoryAbility")
                    .build();
            intent.setOperation(operation);
            // ???????????????????????? Ability???????????????????????????
            intent.setFlags(Intent.FLAG_ABILITY_CLEAR_MISSION | Intent.FLAG_ABILITY_NEW_MISSION);
            startAbility(intent);
        });
        // ?????? uploadItemLayout
        uploadItemLayout.setClickedListener( component -> {
            Toast.makeToast(abilitySlice, "??????uploadItemLayout", Toast.TOAST_SHORT).show();
            /* if (verifyCallingPermission("ohos.permission.CAMERA") != IBundleManager.PERMISSION_GRANTED ||
                verifyCallingPermission("ohos.permission.READ_MEDIA") != IBundleManager.PERMISSION_GRANTED ||
                verifyCallingPermission("ohos.permission.WRITE_MEDIA") != IBundleManager.PERMISSION_GRANTED) {
                // ????????????
                HiLog.error(LOG_LABEL, "????????????");
            } */
            if (verifySelfPermission("ohos.permission.WRITE_USER_STORAGE") != IBundleManager.PERMISSION_GRANTED ||
                    verifySelfPermission("ohos.permission.CAMERA") != IBundleManager.PERMISSION_GRANTED ||
                    verifySelfPermission("ohos.permission.READ_MEDIA") != IBundleManager.PERMISSION_GRANTED) {

                HiLog.debug(LOG_LABEL, "?????????????????????????????????");
                // ????????????
                if (canRequestPermission("ohos.permission.READ_MEDIA")) {
                    // ohos.permission.READ_MEDIA ??????????????????
                    HiLog.debug(LOG_LABEL, "ohos.permission.READ_MEDIA ??????????????????");
                    requestPermissionsFromUser(
                            new String[]{"ohos.permission.READ_MEDIA",
                                    "ohos.permission.WRITE_MEDIA",
                                    "ohos.permission.MEDIA_LOCATION",
                                    "ohos.permission.CAMERA",
                                    "ohos.permission.WRITE_USER_STORAGE"
                            }, MY_PERMISSIONS_REQUEST_CAMERA);
                } else {
                    // ohos.permission.READ_MEDIA ?????????????????????
                    HiLog.warn(LOG_LABEL, "ohos.permission.READ_MEDIA ?????????????????????");
                }
            } else {
                // ?????????
                HiLog.debug(LOG_LABEL, "??????????????????????????????");
                Matisse.from(abilitySlice)
                        .choose(MimeType.ofAll())
                        .addFilter(new GifSizeFilter(320, 320, 5 * Filter.K * Filter.K))
                        .countable(true)
                        .capture(true)
                        .maxSelectable(9)
                        .originalEnable(false)
                        .forResult(REQUEST_CODE_CHOOSE);
            }
        });
    }

    /**
     * ?????? Ability ??????????????????
     * @param requestCode
     * @param resultCode
     * @param resultData
     */
    @Override
    protected void onAbilityResult(int requestCode, int resultCode, Intent resultData) {
        super.onAbilityResult(requestCode, resultCode, resultData);

        switch (requestCode){
            case REQUEST_CODE_CHOOSE:
                if (resultCode == MatisseAbility.RESULT_OK){
                    mSelected = Matisse.obtainResult(resultData);
                    ArrayList<Uri> uriArrayList = resultData.getSequenceableArrayListParam(MatisseAbility.EXTRA_RESULT_SELECTION);
                    // stringArrayList ???????????????????????????
                    ArrayList<String> stringArrayList = resultData.getStringArrayListParam(MatisseAbility.EXTRA_RESULT_SELECTION_PATH);
                    HiLog.debug(LOG_LABEL, "mSelected: " + mSelected);
                    HiLog.debug(LOG_LABEL, "uriArrayList: " + uriArrayList);
                    HiLog.debug(LOG_LABEL, "stringArrayList: " + stringArrayList);

                    // ??????????????????????????????????????????????????????????????????????????????
                    getGlobalTaskDispatcher(TaskPriority.DEFAULT)
                            .asyncDispatch( () -> {
                                try {
                                    isFileExist(uriArrayList.get(0), stringArrayList.get(0));
                                } catch (IOException ex){
                                    HiLog.error(LOG_LABEL, String.format("?????????????????????????????????????????????%s", ex.getMessage()));
                                    ex.printStackTrace();
                                } catch (DataAbilityRemoteException ex){
                                    HiLog.error(LOG_LABEL, String.format("?????????????????????????????????%s, ?????????%s",stringArrayList.get(0), ex.getMessage()));
                                }
                            });
                } else {
                    HiLog.warn(LOG_LABEL, String.format("??????????????????????????????????????????resultCode=%s, resultData=%s", String.valueOf(resultCode),
                            resultData == null ? "null" : resultData.toString()
                    ));
                }
                break;

            default:
                break;
        }
    }

    /**
     * ????????????
     * @param fileUri
     * @param filePath
     * @param fileId
     */
    private void uploadFile(Uri fileUri, String filePath, String fileId){

        /* InputStream in = null;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try{
            in = new FileInputStream(filePath);
            byte[] buf = new byte[1024];
            int length = 0;
            while ((length = in.read(buf)) != -1){
                out.write(buf, 0, length);
            }
        } catch (Exception ex){
            HiLog.error(LOG_LABEL, String.format("11111111111111111????????????%s??????????????????????????????%s", filePath, ex.getMessage()));
            ex.printStackTrace();
        } finally {
            if (in != null){
                try {
                    in.close();
                } catch (IOException ex){
                    HiLog.error(LOG_LABEL, String.format("22222222222222222222222????????????????????????%s", ex.getMessage()));
                    ex.printStackTrace();
                }
            }
        }
        byte[] outByte = out.toByteArray();
        HiLog.debug(LOG_LABEL, String.format("byte: %s", outByte.toString())); */


        // ??????
        /* filePath = "assets/entry/resources/rawfile/pan/test.jpg";
        File file = new File(filePath);
        HiLog.debug(LOG_LABEL, file.getAbsolutePath());
        HiLog.debug(LOG_LABEL, file.exists() ? "??????" : "?????????");
        HiLog.debug(LOG_LABEL, file.canRead() ? "??????" : "?????????");
        HiLog.debug(LOG_LABEL, file.canWrite() ? "??????" : "?????????");
        HiLog.debug(LOG_LABEL, file.canExecute() ? "?????????" : "????????????");

        ResourceManager resourceManager = abilitySlice.getResourceManager();
        RawFileEntry rawFileEntry = resourceManager.getRawFileEntry("resources/rawfile/pan/test.jpg"); */

        try{
            /* Resource resource = rawFileEntry.openRawFile();
            int len = resource.available();
            byte[] buffer = new byte[len];
            resource.read(buffer, 0, len);

            HiLog.debug(LOG_LABEL, FastDFSUtil.uploadBytes(buffer, 0, len, "jpg")); */


            // ?????????????????????
            String fileExtName = "";
            // ???????????????
            String fileName = "";
            try {
                fileExtName = filePath.substring(filePath.lastIndexOf(".")+1);
                fileName = filePath.substring(filePath.lastIndexOf("/")+1);
            } catch (Exception e){
                HiLog.warn(LOG_LABEL, String.format("??????%s??????????????????????????????????????????%s", filePath, e.getMessage()));
                e.printStackTrace();
                fileExtName = "unknown";
                fileName = "unknown";
            }

            FileDescriptor fd = helper.openFile(fileUri, "r");
            FileInputStream inputStream = new FileInputStream(fd);
            HiLog.debug(LOG_LABEL, String.format("????????????????????????%d", inputStream.available()));

            // ????????????
            String fileUrl = FastDFSUtil.uploadByStream(fileExtName, inputStream);
            HiLog.debug(LOG_LABEL, String.format("??????????????????????????????%s", fileUrl));

            // ?????? FileInputStream ???????????????????????????int???????????????1.9G????????? available
            // ????????????????????? java.nio.*?????????????????????FileChannel
            FileChannel fileChannel = inputStream.getChannel();

            // ????????????????????????
            File file = new File();
            file.setFileId(fileId);
            file.setFileName(fileName);
            file.setFileType(fileExtName);
            file.setFileSize(fileChannel.size());
            file.setUrl(fileUrl);
            inputStream.close();

            addFileEntity(file);
        } catch (IOException ex){
            HiLog.error(LOG_LABEL, String.format("?????????????????????????????????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        } catch (MyException ex){
            HiLog.error(LOG_LABEL, String.format("????????????MyException??????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        } catch (Exception ex){
            // [TODO] ????????????Exception??????????????????Attempt to invoke virtual method 'java.lang.String java.net.InetAddress.getHostAddress()' on a null object reference
            HiLog.error(LOG_LABEL, String.format("????????????Exception??????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        }

        /* FastDFSFile fastDFSFile = new FastDFSFile();
        fastDFSFile.setName(filePath);
        fastDFSFile.setExt("jpg");
        try {
            HiLog.debug(LOG_LABEL, FastDFSUtil.upload(fastDFSFile));
        } catch (IOException ex){
            HiLog.error(LOG_LABEL, String.format("????????????IOException??????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        } catch (MyException ex){
            HiLog.error(LOG_LABEL, String.format("????????????MyException??????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        } catch (Exception ex){
            HiLog.error(LOG_LABEL, String.format("????????????Exception??????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        }*/
    }

    /**
     * ????????????md5????????????????????????????????????
     * @param fileUri
     * @param filePath
     *      string[0] exist "yes" "no"
     *      string[1] fileId
     */
    private void isFileExist(Uri fileUri, String filePath) throws IOException, DataAbilityRemoteException {
        FileDescriptor fd = helper.openFile(fileUri, "r");
        // ???????????????????????????????????????
        FileInputStream inputStream = new FileInputStream(fd);

        HiLog.debug(LOG_LABEL, String.format("??????????????????????????????%d", inputStream.available()));
        // inputStream.mark(0);
        // ???????????? md5???
        String md5 = DigestUtils.md5Hex(inputStream);
        HiLog.debug(LOG_LABEL, String.format("??????md5??????%s", md5));
        // inputStream.reset();
        HiLog.debug(LOG_LABEL, String.format("??????????????????????????????%d", inputStream.available()));
        inputStream.close();

        String url = String.format("%s/%s", GET_FILE_ID_URL, md5);
        String accessToken = tokenDao.get().getAccessToken();
        Map<String, String> addHeaders = new HashMap<>();
        String Authorization = String.format("Bearer %s", accessToken);
        addHeaders.put("Authorization", Authorization);
        HttpUtil.get(url, null, addHeaders, new okhttp3.Callback(){
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e){
                HiLog.error(LOG_LABEL, e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                String responseStr = response.body().string();
                if (response.isSuccessful()){
                    // ?????? JSON ?????????
                    JSONObject object = JSONObject.parseObject(responseStr);
                    String exist = object.getString("exist");
                    HiLog.debug(LOG_LABEL, String.format("exist: %s", exist));

                    if ("yes".equals(exist)){
                        // ???????????????
                        // ???????????????????????????????????????
                        String fileJson = object.getString("file");
                        File file = JSON.parseObject(fileJson, File.class);
                        addFileLinkOfFolder(file);
                    } else if ("no".equals(exist)){
                        // ???????????????
                        // ????????????
                        String fileId = object.getString("fileId");
                        HiLog.debug(LOG_LABEL, String.format("????????????????????????fileId: %s", fileId));
                        uploadFile(fileUri, filePath, fileId);
                    } else {
                        HiLog.error(LOG_LABEL, String.format("exist: %s", exist));
                    }
                } else {
                    HiLog.error(LOG_LABEL, responseStr);
                }
            }
        });
    }

    /**
     * ????????????????????????
     * @param file
     */
    private void addFileEntity(File file){
        String fileJson = JSON.toJSONString(file);
        HiLog.debug(LOG_LABEL, String.format("fileJson: %s", fileJson));

        RequestBody requestBody = RequestBody.create(HttpUtil.JSON, fileJson);
        String accessToken = tokenDao.get().getAccessToken();
        Map<String, String> addHeaders = new HashMap<>();
        String Authorization = String.format("Bearer %s", accessToken);
        addHeaders.put("Authorization", Authorization);
        HttpUtil.post(ADD_FILE_URL, requestBody,null, addHeaders, new okhttp3.Callback(){
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e){
                HiLog.error(LOG_LABEL, e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                String responseStr = response.body().string();
                if (response.isSuccessful()){
                    // ?????? JSON ?????????
                    File file = JSON.parseObject(responseStr, File.class);
                    HiLog.debug(LOG_LABEL, String.format("???????????????????????????%s", file.toString()));

                    // ???????????????????????????????????????
                    addFileLinkOfFolder(file);
                } else {
                    HiLog.error(LOG_LABEL, responseStr);
                }
            }
        });
    }

    /**
     * ???????????????????????????????????????
     * @param file
     */
    private void addFileLinkOfFolder(File file){
        if (file == null){
            HiLog.error(LOG_LABEL, "??????????????????????????????????????????file??????");
            return;
        }

        FileLink fileLink = new FileLink();
        fileLink.setLinkName(file.getFileName());
        fileLink.setFileId(file.getFileId());
        fileLink.setFileType(file.getFileType());
        fileLink.setFileSize(file.getFileSize());
        List<FileLink> fileLinks = new ArrayList<>();
        fileLinks.add(fileLink);

        FolderChildren children = new FolderChildren();
        children.setFiles(fileLinks);

        FolderLink folderLink1 = new FolderLink();
        folderLink1.setFolderId(folderLink.getFolderId());
        folderLink1.setUid(folderLink.getUid());
        folderLink1.setChildren(children);

        String folderLink1Json = JSON.toJSONString(folderLink1);
        RequestBody requestBody = RequestBody.create(HttpUtil.JSON, folderLink1Json);
        String accessToken = tokenDao.get().getAccessToken();
        Map<String, String> addHeaders = new HashMap<>();
        String Authorization = String.format("Bearer %s", accessToken);
        addHeaders.put("Authorization", Authorization);

        HttpUtil.post(ADD_FOLDER_CHILDREN, requestBody, null, addHeaders, new okhttp3.Callback(){
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e){
                HiLog.error(LOG_LABEL, e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                String responseStr = response.body().string();
                if (response.isSuccessful()){
                    // ?????? JSON ?????????
                    JSONObject object = JSONObject.parseObject(responseStr);
                    int status = object.getIntValue("status");
                    HiLog.debug(LOG_LABEL, String.format("status: %d", status));
                    if (status <= 0){
                        // ????????????
                        HiLog.debug(LOG_LABEL, "???????????????????????????????????????????????????");
                    } else {
                        HiLog.debug(LOG_LABEL, "???????????????????????????????????????????????????");

                        // ?????? folderLink ??????????????????????????????????????????????????????????????????????????????
                        FolderChildren children1 = object.getObject("children", FolderChildren.class);

                        // ????????????????????????
                        insert_fileUpload(children1.getFiles().get(0));

                        // ????????????????????????
                        query_fileUpload();

                        // ???????????????UI????????????????????????????????????
                        TaskDispatcher uiTaskDispatcher = getUITaskDispatcher();
                        uiTaskDispatcher.asyncDispatch(() -> {
                            Toast.makeToast(abilitySlice, String.format("??????%s???????????????", file.getFileName()), Toast.TOAST_SHORT).show();
                        });

                        // ?????????????????????
                        getFolderLink();
                    }
                } else {
                    HiLog.error(LOG_LABEL, responseStr);
                }
            }
        });

    }

    /**
     * ???????????????????????????????????????????????? ListContainer
     */
    private void setListContainer(){
        if (folderLink == null){
            HiLog.warn(LOG_LABEL, "folderLink ??????");
            return;
        }
        // ????????????????????? folderItemProvider ??? fileItemProvider???JAVA ????????????
        FolderItemView folderItemView = new FolderItemView(folderLink.getChildren().getFolders());
        // folderItemProvider = new FolderItemProvider(folderItemView.getFolderItemInfos());
        FileItemView fileItemView = new FileItemView(folderLink.getChildren().getFiles());
        // fileItemProvider = new FileItemProvider(fileItemView.getFileItemInfos());
        childItemProvider = new ChildItemProvider(folderItemView.getFolderItemInfos(), fileItemView.getFileItemInfos(),
                FileSystemAbilitySlice.this);

        // foldersContainer.setItemProvider(folderItemProvider);
        // filesContainer.setItemProvider(fileItemProvider);
        childrenContainer.setItemProvider(childItemProvider);

        // ?????? ListContainer ??????????????????
        setListClickListener();

        // ??????????????????????????????
        setListReboundAnimation();
    }

    /**
     * ?????? ListContainer ??????????????????
     */
    private void setListClickListener(){
        // foldersContainer ??????????????????
        /* foldersContainer.setItemClickedListener( (listContainer, component, position, id) -> {
            FolderItemInfo folderItemInfo = (FolderItemInfo) folderItemProvider.getItem(position);
            Toast.makeToast(abilitySlice, folderItemInfo.toString(), Toast.TOAST_SHORT).show();
        }); */

        // filesContainer ??????????????????
        /* filesContainer.setItemClickedListener( (listContainer, component, position, id) -> {
            FileItemInfo fileItemInfo = (FileItemInfo) fileItemProvider.getItem(position);
            Toast.makeToast(abilitySlice, fileItemInfo.toString(), Toast.TOAST_SHORT).show();
        }); */

        // childrenContainer ??????????????????
        childrenContainer.setItemClickedListener( (listContainer, component, position, id) -> {
            Object object = childItemProvider.getItem(position);
            // Toast.makeToast(abilitySlice, object.toString(), Toast.TOAST_LONG).show();
            HiLog.debug(LOG_LABEL, String.format("position: %d, object: %s", position, object.toString()));
        });

        // childrenContainer ??????????????????
        childrenContainer.setItemLongClickedListener( (listContainer, component, position, id) -> {
            if (childItemProvider.isFolder(position)){
                // ??????????????????
                FolderItemInfo folderItemInfo = (FolderItemInfo) childItemProvider.getItem(position);
                folderItemInfo.setChecked(!folderItemInfo.isChecked());
                selectedItemCount += folderItemInfo.isChecked() ? 1 : -1;
            } else {
                // ???????????????
                FileItemInfo fileItemInfo = (FileItemInfo) childItemProvider.getItem(position);
                fileItemInfo.setChecked(!fileItemInfo.isChecked());
                selectedItemCount += fileItemInfo.isChecked() ? 1 : -1;
            }
            childItemProvider.notifyDataSetItemChanged(position);
            if (!isItemSelected){
                setSelectedBottomToolBar();
            }
            if (selectedItemCount <= 0){
                setBottomToolBar();
            }
            return false;
        });
    }

    /**
     * ??????????????????????????????
     */
    private void setListReboundAnimation() {
        // foldersContainer.setReboundEffect(true);
        // foldersContainer.setReboundEffectParams(OVER_SCROLL_PERCENT, OVER_SCROLL_RATE, REMAIN_VISIBLE_PERCENT);

        // filesContainer.setReboundEffect(true);
        // filesContainer.setReboundEffectParams(OVER_SCROLL_PERCENT, OVER_SCROLL_RATE, REMAIN_VISIBLE_PERCENT);

        childrenContainer.setReboundEffect(true);
        childrenContainer.setReboundEffectParams(OVER_SCROLL_PERCENT, OVER_SCROLL_RATE, REMAIN_VISIBLE_PERCENT);
    }

    /**
     * ?????????????????????
     */
    private void getFolderLink(){
        String url = String.format("%s/%s", GET_FOLDER_LINK_URL, "7616b8db-fa5b-48a2-a2ec-4ac839cbf4b7");
        String accessToken = tokenDao.get().getAccessToken();
        Map<String, String> addHeaders = new HashMap<>();
        String Authorization = String.format("Bearer %s", accessToken);
        addHeaders.put("Authorization", Authorization);
        HttpUtil.get(url, null, addHeaders, new okhttp3.Callback(){
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e){
                HiLog.error(LOG_LABEL, e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                String responseStr = response.body().string();
                if (response.isSuccessful()){
                    HiLog.debug(LOG_LABEL, responseStr);
                    folderLink = JSON.parseObject(responseStr, FolderLink.class);
                    HiLog.debug(LOG_LABEL, folderLink.toString());
                    HiLog.debug(LOG_LABEL, String.format("file size: %d", folderLink.getChildren().getFiles().size()));

                    // ?????? ListContainer
                    // ????????????(UI??????)?????????
                    TaskDispatcher uiTaskDispatcher = getUITaskDispatcher();
                    uiTaskDispatcher.asyncDispatch(() -> {
                        setListContainer();
                    });
                } else {
                    HiLog.error(LOG_LABEL, responseStr);
                }
            }
        });
    }

    /**
     * ?????????????????????
     */
    private void setBottomToolBar(){
        bottomBar.setVisibility(Component.VISIBLE);
        bottomBarFS.setVisibility(Component.HIDE);
        isItemSelected = false;
        selectedItemCount = 0;

        BottomBarItemView bottomBarItemView = new BottomBarItemView();
        bottomBarItemInfoList = bottomBarItemView.getBottomBarItemInfos();

        IntStream.range(0, bottomBarItemInfoList.size()).forEach( position -> {
            DirectionalLayout bottomItemLayout = (DirectionalLayout) abilitySlice.findComponentById(
                    bottomBarItemInfoList.get(position).getBnavLayoutId());
            // bottomItemLayout.setVisibility(Component.VISIBLE);
            Image image = (Image) bottomItemLayout.findComponentById(
                    bottomBarItemInfoList.get(position).getBnavImgId());
            Text text = (Text) bottomItemLayout.findComponentById(
                    bottomBarItemInfoList.get(position).getBnavTextId());
            if (position == 0){
                // ????????????
                image.setImageAndDecodeBounds(
                        bottomBarItemInfoList.get(position).getBnavActivatedImgSrcId());
                text.setTextColor(Color.BLUE);
            } else {
                image.setImageAndDecodeBounds(
                        bottomBarItemInfoList.get(position).getBnavImgSrcId());
            }

            // ????????????????????????
            bottomItemLayout.setClickedListener( component -> {
                unselected();
                HiLog.debug(LOG_LABEL, "???????????????????????????" + position);
                image.setImageAndDecodeBounds(
                        bottomBarItemInfoList.get(position).getBnavActivatedImgSrcId());
                text.setTextColor(Color.BLUE);
                startAbilityFromBnav(position);
            });
        });
    }

    /**
     * ?????????????????????????????????
     * @param position
     */
    private void startAbilityFromBnav(int position){
        if (position == 0){
            return;
        } else if (position == 1){
            Intent intent = new Intent();
            Operation operation = new Intent.OperationBuilder()
                    .withDeviceId("")
                    .withBundleName("cn.qixqi.pan")
                    .withAbilityName("cn.qixqi.pan.FileSharingAbility")
                    .build();
            intent.setOperation(operation);
            // ???????????????????????? Ability???????????????????????????
            intent.setFlags(Intent.FLAG_ABILITY_CLEAR_MISSION | Intent.FLAG_ABILITY_NEW_MISSION);
            startAbility(intent);
        } else if (position == 2) {
            Intent intent = new Intent();
            Operation operation = new Intent.OperationBuilder()
                    .withDeviceId("")
                    .withBundleName("cn.qixqi.pan")
                    .withAbilityName("cn.qixqi.pan.ProfileAbility")
                    .build();
            intent.setOperation(operation);
            // ???????????????????????? Ability???????????????????????????
            intent.setFlags(Intent.FLAG_ABILITY_CLEAR_MISSION | Intent.FLAG_ABILITY_NEW_MISSION);
            startAbility(intent);
        }
    }

    /**
     * ???????????????????????????????????????
     */
    private void unselected(){
        IntStream.range(0, bottomBarItemInfoList.size()).forEach( position -> {
            DirectionalLayout bottomItemLayout = (DirectionalLayout) abilitySlice.findComponentById(
                    bottomBarItemInfoList.get(position).getBnavLayoutId());
            Image image = (Image) bottomItemLayout.findComponentById(
                    bottomBarItemInfoList.get(position).getBnavImgId());
            Text text = (Text) bottomItemLayout.findComponentById(
                    bottomBarItemInfoList.get(position).getBnavTextId());
            image.setPixelMap(bottomBarItemInfoList.get(position).getBnavImgSrcId());
            text.setTextColor(Color.BLACK);
        });
    }

    /**
     * ???????????????????????????
     */
    private void setSelectedBottomToolBar(){
        bottomBar.setVisibility(Component.HIDE);
        bottomBarFS.setVisibility(Component.VISIBLE);
        // ??????????????????????????????
        isItemSelected = true;

        BottomBarFSItemView bottomBarFSItemView = new BottomBarFSItemView();
        bottomBarFSItemInfoList = bottomBarFSItemView.getBottomBarItemInfos();

        IntStream.range(0, bottomBarFSItemInfoList.size()).forEach( position -> {
            DirectionalLayout bottomItemLayout = (DirectionalLayout) abilitySlice.findComponentById(
                    bottomBarFSItemInfoList.get(position).getBnavLayoutId());
            // bottomItemLayout.setVisibility(Component.VISIBLE);
            Image image = (Image) bottomItemLayout.findComponentById(
                    bottomBarFSItemInfoList.get(position).getBnavImgId());
            Text text = (Text) bottomItemLayout.findComponentById(
                    bottomBarFSItemInfoList.get(position).getBnavTextId());

            // ????????????????????????
            bottomItemLayout.setClickedListener( component -> {
                // Toast.makeToast(abilitySlice, String.format("?????????%s", text.getText()), Toast.TOAST_SHORT).show();
                clickActionFromBnav(position);
            });
        });
    }

    /**
     * ???????????????????????????
     * @param position
     */
    private void clickActionFromBnav(int position){
        switch (position){
            case 0:
                HiLog.debug(LOG_LABEL, "???????????????????????? ...");
                selectedChildren = childItemProvider.getSelectedChildren();
                HiLog.debug(LOG_LABEL, selectedChildren.toString());
                startDownload(selectedChildren);
                break;
            case 1:
                HiLog.debug(LOG_LABEL, "???????????????????????? ...");
                selectedChildren = childItemProvider.getSelectedChildren();
                HiLog.debug(LOG_LABEL, selectedChildren.toString());
                // ??????????????????????????????????????????
                getGlobalTaskDispatcher(TaskPriority.DEFAULT)
                        .asyncDispatch( () -> {
                            startShare(cloneChildren(selectedChildren));
                        });
                break;
            case 2:
                HiLog.debug(LOG_LABEL, "???????????????????????? ...");
                selectedChildren = childItemProvider.getSelectedChildren();
                HiLog.debug(LOG_LABEL, selectedChildren.toString());
                // ??????????????????????????????????????????
                getGlobalTaskDispatcher(TaskPriority.DEFAULT)
                        .asyncDispatch( () -> {
                            startDelete(cloneChildren(selectedChildren));
                        });
                break;
            case 3:
                HiLog.debug(LOG_LABEL, "??????????????????????????? ...");
                selectedChildren = childItemProvider.getSelectedChildren();
                HiLog.debug(LOG_LABEL, selectedChildren.toString());
                startRename(cloneChildren(selectedChildren));
                break;
            default:
                HiLog.error(LOG_LABEL, "????????????????????????????????????");
                break;
        }
    }

    /**
     * ?????????????????????
     * @param children
     */
    private void startRename(FolderChildren children){
        HiLog.debug(LOG_LABEL, "?????????????????????");
        if (selectedItemCount == 1){
            HiLog.debug(LOG_LABEL, "???????????????????????????");
            // ????????????????????????
            showRenameDialog(children);
        } else {
            HiLog.warn(LOG_LABEL, "??????????????????????????????");
            Toast.makeToast(abilitySlice, "??????????????????????????????", Toast.TOAST_SHORT).show();
        }
    }

    /**
     * ????????????????????????
     * @param children
     */
    private void showRenameDialog(FolderChildren children){
        FileLink fileLink = children.getFiles().get(0);
        CommonDialog renameDialog = new CommonDialog(abilitySlice);
        Component dialogComponent = LayoutScatter.getInstance(abilitySlice)
                .parse(ResourceTable.Layout_dialog_rename, null, false);
        TextField inputNewName = (TextField) dialogComponent.findComponentById(ResourceTable.Id_input_new_name);
        Text cancel = (Text) dialogComponent.findComponentById(ResourceTable.Id_cancel);
        Text rename = (Text) dialogComponent.findComponentById(ResourceTable.Id_rename);
        renameDialog.setContentCustomComponent(dialogComponent);
        renameDialog.setSize(MATCH_PARENT, MATCH_CONTENT);
        renameDialog.setAlignment(LayoutAlignment.CENTER);
        renameDialog.setTransparent(true);
        renameDialog.setCornerRadius(15);
        // renameDialog.setAutoClosable(true);
        inputNewName.setText(fileLink.getLinkName());
        inputNewName.requestFocus();
        // ????????????
        cancel.setClickedListener(component -> {
            Toast.makeToast(abilitySlice, String.format("Click %s", getString(ResourceTable.String_cancel)),
                    Toast.TOAST_SHORT).show();
            renameDialog.destroy();
        });
        // ???????????????
        rename.setClickedListener(component -> {
            Toast.makeToast(abilitySlice, String.format("Click %s, new name: %s", getString(ResourceTable.String_rename),
                    inputNewName.getText().trim()),
                    Toast.TOAST_SHORT).show();
            renameDialog.destroy();
            if (!"".equals(inputNewName.getText().trim()) && !fileLink.getLinkName().equals(inputNewName.getText().trim())){
                fileLink.setLinkName(inputNewName.getText().trim());
                renameFile(children);
            }
        });
        renameDialog.show();
    }

    /**
     * ???????????????
     * @param children
     */
    private void renameFile(FolderChildren children){
        FolderLink renamedFolder = new FolderLink();
        renamedFolder.setFolderId(folderLink.getFolderId());
        renamedFolder.setFolderName(folderLink.getFolderName());
        renamedFolder.setUid(folderLink.getUid());
        renamedFolder.setParent(folderLink.getParent());
        renamedFolder.setCreateTime(folderLink.getCreateTime());
        renamedFolder.setChildren(children);

        String renamedFolderJson = JSON.toJSONString(renamedFolder);
        HiLog.debug(LOG_LABEL, String.format("renamedFolderJson: %s", renamedFolderJson));

        RequestBody requestBody = RequestBody.create(HttpUtil.JSON, renamedFolderJson);
        String accessToken = tokenDao.get().getAccessToken();
        Map<String, String> addHeaders = new HashMap<>();
        String Authorization = String.format("Bearer %s", accessToken);
        addHeaders.put("Authorization", Authorization);
        HttpUtil.put(RENAME_FILE, requestBody, null, addHeaders, new okhttp3.Callback(){
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e){
                HiLog.error(LOG_LABEL, e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                String responseStr = response.body().string();
                JSONObject object = JSONObject.parseObject(responseStr);
                long status = object.getLongValue("status");
                if (status > 0){
                    HiLog.debug(LOG_LABEL, "?????????????????????");
                    // ?????????????????????????????????????????????
                    // [TODO] ??????????????????????????????????????????
                    getFolderLink();
                } else {
                    // ????????????????????? JSON?????????????????? status
                    HiLog.warn(LOG_LABEL, "?????????????????????");
                }
            }
        });
    }

    /**
     * ??????????????????
     * @param children
     */
    private void startDelete(FolderChildren children){
        HiLog.debug(LOG_LABEL, "??????????????????");
        // ?????????
        children.getFolders().clear();
        // ??????
        FolderLink deletedFolder = new FolderLink();
        deletedFolder.setFolderId(folderLink.getFolderId());
        deletedFolder.setFolderName(folderLink.getFolderName());
        deletedFolder.setUid(folderLink.getUid());
        deletedFolder.setParent(folderLink.getParent());
        deletedFolder.setCreateTime(folderLink.getCreateTime());
        deletedFolder.setChildren(children);
        // ????????????
        deleteFile(deletedFolder);
    }

    /**
     * ????????????
     * @param deletedFolder
     */
    private void deleteFile(FolderLink deletedFolder){
        String deletedFolderJson = JSON.toJSONString(deletedFolder);
        HiLog.debug(LOG_LABEL, String.format("deletedFolderJson: %s", deletedFolderJson));

        RequestBody requestBody = RequestBody.create(HttpUtil.JSON, deletedFolderJson);
        String accessToken = tokenDao.get().getAccessToken();
        Map<String, String> addHeaders = new HashMap<>();
        String Authorization = String.format("Bearer %s", accessToken);
        addHeaders.put("Authorization", Authorization);
        HttpUtil.delete(DELETE_FILE, requestBody, null, addHeaders, new okhttp3.Callback(){
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e){
                HiLog.error(LOG_LABEL, e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                String responseStr = response.body().string();
                JSONObject object = JSONObject.parseObject(responseStr);
                long status = object.getLongValue("status");
                if (status > 0){
                    HiLog.debug(LOG_LABEL, "??????????????????");
                    // ?????????????????????????????????????????????
                    // [TODO] ??????????????????????????????????????????
                    getFolderLink();
                } else {
                    // ????????????????????? JSON?????????????????? status
                    HiLog.warn(LOG_LABEL, "??????????????????");
                }
            }
        });
    }

    /**
     * FolderChildren ??????
     * @param children
     * @return
     */
    private FolderChildren cloneChildren(FolderChildren children){
        FolderChildren copy = new FolderChildren();
        for (SimpleFolderLink simpleFolderLink : children.getFolders()){
            copy.addFolder(new SimpleFolderLink(simpleFolderLink));
        }
        for (FileLink fileLink : children.getFiles()){
            copy.addFile(new FileLink(fileLink));
        }
        return copy;
    }

    /**
     * ??????????????????
     * @param children
     */
    private void startShare(FolderChildren children){
        HiLog.debug(LOG_LABEL, "??????????????????");
        // ?????????
        children.getFolders().clear();
        // ??????
        for (FileLink fileLink : children.getFiles()){
            fileLink.setLinkId(null);
            fileLink.setCreateTime(null);
        }
        FileShareLink fileShareLink = new FileShareLink();
        fileShareLink.setChildren(children);
        HiLog.debug(LOG_LABEL, String.format("fileShareLink: %s", fileShareLink.toString()));
        // ????????????
        shareFile(fileShareLink);
    }

    /**
     * ????????????
     * @param fileShareLink
     */
    private void shareFile(FileShareLink fileShareLink){
        String fileShareLinkJson = JSON.toJSONString(fileShareLink);
        HiLog.debug(LOG_LABEL, String.format("fileShareLinkJson: %s", fileShareLinkJson));

        RequestBody requestBody = RequestBody.create(HttpUtil.JSON, fileShareLinkJson);
        String accessToken = tokenDao.get().getAccessToken();
        Map<String, String> addHeaders = new HashMap<>();
        String Authorization = String.format("Bearer %s", accessToken);
        addHeaders.put("Authorization", Authorization);
        HttpUtil.post(SHARE_FILE, requestBody, null, addHeaders, new okhttp3.Callback(){
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e){
                HiLog.error(LOG_LABEL, e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                String responseStr = response.body().string();
                JSONObject object = JSONObject.parseObject(responseStr);
                int status = object.getIntValue("status");
                if (status == 1) {
                    HiLog.debug(LOG_LABEL, "??????????????????");
                } else {
                    // ????????????????????? JSON?????????????????? status
                    HiLog.warn(LOG_LABEL, "??????????????????");
                }
            }
        });
    }

    /**
     * ??????????????????
     * @param children
     */
    private void startDownload(FolderChildren children){
        HiLog.debug(LOG_LABEL, "****????????????????????????");
        // ???????????????
        int test = 0;
        for (FileLink fileLink : children.getFiles()){
            HiLog.debug(LOG_LABEL, String.format("*********?????????????????????%s", fileLink.toString()));
            if (test == 0){
                // ??????????????????????????????????????????
                getGlobalTaskDispatcher(TaskPriority.DEFAULT)
                        .asyncDispatch( () -> {
                            getFileUrl(fileLink);
                        });
            }
            test ++;
        }
        // ??????????????????
        for (SimpleFolderLink simpleFolderLink : children.getFolders()){
            HiLog.debug(LOG_LABEL, String.format("****????????????????????????%s", simpleFolderLink.toString()));
        }
    }

    /**
     * ???????????????????????????????????????????????????
     * @param fileLink
     */
    private void getFileUrl(FileLink fileLink){
        String url = String.format(GET_FILE_URL, fileLink.getFileId());
        String accessToken = tokenDao.get().getAccessToken();
        Map<String, String> addHeaders = new HashMap<>();
        String Authorization = String.format("Bearer %s", accessToken);
        addHeaders.put("Authorization", Authorization);
        HttpUtil.get(url, null, addHeaders, new okhttp3.Callback(){
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e){
                HiLog.error(LOG_LABEL, String.format("?????????????????????%s", fileLink.toString()));
                HiLog.error(LOG_LABEL, e.getMessage());
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException{
                String responseStr = response.body().string();
                if (response.isSuccessful()){
                    // HiLog.debug(LOG_LABEL, String.format("responseStr: %s", responseStr));
                    if (responseStr == null || "".equals(responseStr.trim())){
                        HiLog.warn(LOG_LABEL, String.format("????????????????????????%s", fileLink.toString()));
                    } else {
                        String fileUrl = responseStr;
                        HiLog.debug(LOG_LABEL, String.format("?????????????????????%s", fileUrl));
                        // ????????????????????????????????????
                        getGlobalTaskDispatcher(TaskPriority.DEFAULT)
                                .asyncDispatch( () -> {
                                    downloadFile(fileUrl, fileLink);
                                });
                    }
                } else {
                    HiLog.error(LOG_LABEL, String.format("?????????????????????%s", fileLink.toString()));
                    HiLog.error(LOG_LABEL, responseStr);
                }
            }
        });
    }

    /**
     * ????????????
     * @param fileUrl
     * @param fileLink
     */
    private void downloadFile(String fileUrl, FileLink fileLink){
        int index = fileUrl.indexOf('/');
        String groupName = fileUrl.substring(0, index);
        String remoteFileName = fileUrl.substring(index + 1);
        HiLog.debug(LOG_LABEL, String.format("group_name: %s, remote_file_name: %s", groupName, remoteFileName));
        // ??????????????????
        String filePath = rootDir + java.io.File.separator + fileLink.getLinkName();
        HiLog.debug(LOG_LABEL, String.format("?????????????????????%s", filePath));
        java.io.File fileDir = new java.io.File(rootDir);
        if (!fileDir.exists()){
            // ?????????????????????
            fileDir.mkdirs();
        }
        java.io.File file = new java.io.File(filePath);
        if (file.exists()){
            // ???????????????
            HiLog.debug(LOG_LABEL, String.format("????????????????????????%s", fileLink.getLinkName()));
            return;
        }
        try (FileOutputStream outputStream = new FileOutputStream(file)){
            // ??????????????????????????????
            byte[] fileBytes = FastDFSUtil.download(groupName, remoteFileName);
            outputStream.write(fileBytes);
            HiLog.debug(LOG_LABEL, String.format("????????????%s????????????????????????%d", fileLink.getLinkName(), fileBytes.length));
            // ????????????????????????
            insert_fileDownload(fileLink);
            // ????????????????????????
            query_fileDownload();

            // ????????????????????????
            test(fileLink.getLinkName());
        } catch (IOException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????IOException?????????%s", ex.getMessage()));
            ex.printStackTrace();
        } catch (MyException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????MyException?????????%s", ex.getMessage()));
            ex.printStackTrace();
        }
    }

    private void test(String linkName){
        try{
            Uri uri = Uri.parse("dataability:///cn.qixqi.pan.data.FileDataAbility/download?" + linkName);
            FileDescriptor fd = helper.openFile(uri, "r");
            FileInputStream inputStream = new FileInputStream(fd);
            HiLog.debug(LOG_LABEL, String.format("---------???????????????????????????size: %d", inputStream.available()));
            inputStream.close();
        } catch (Exception e){
            e.printStackTrace();
            HiLog.error(LOG_LABEL, String.format("---------?????????????????????????????????%s", e.getMessage()));
        }
    }

    /**
     * ????????????????????????
     * @param fileLink
     */
    private void insert_fileDownload(FileLink fileLink){
        ValuesBucket values = new ValuesBucket();
        values.putString("linkId", fileLink.getLinkId());
        values.putString("linkName", fileLink.getLinkName());
        values.putString("fileId", fileLink.getFileId());
        values.putString("fileType", fileLink.getFileType());
        values.putLong("fileSize", fileLink.getFileSize());
        values.putLong("downloadFinishTime", new Date().getTime());
        // [TODO] downloadStatus ??????????????????????????????
        values.putString("downloadStatus", "????????????");
        try {
            int result = helper.insert(Uri.parse(DOWNLOAD_BASE_URI + DOWNLOAD_DATA_PATH), values);
            if (result != -1){
                HiLog.info(LOG_LABEL, "????????????????????????????????????");
            } else {
                HiLog.warn(LOG_LABEL, "????????????????????????????????????");
            }
        } catch (DataAbilityRemoteException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????????????????DataAbilityRemoteException????????????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        } catch (IllegalStateException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????????????????IllegalStateException????????????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        }
    }

    /**
     * ????????????????????????
     */
    private void query_fileDownload(){
        // ????????????
        String[] columns = new String[] {
                "downloadId", "linkId", "linkName", "fileId", "fileType", "fileSize", "downloadFinishTime", "downloadStatus" };
        // ????????????
        DataAbilityPredicates predicates = new DataAbilityPredicates();
        try {
            ResultSet resultSet = helper.query(Uri.parse(DOWNLOAD_BASE_URI + DOWNLOAD_DATA_PATH), columns, predicates);
            if (resultSet == null){
                HiLog.debug(LOG_LABEL, "query: resultSet is null");
                return;
            } else if (resultSet.getRowCount() == 0){
                HiLog.debug(LOG_LABEL, "query: resultSet is no result found");
                return;
            }
            resultSet.goToFirstRow();
            do {
                int downloadId = resultSet.getInt(resultSet.getColumnIndexForName("downloadId"));
                String linkId = resultSet.getString(resultSet.getColumnIndexForName("linkId"));
                String linkName = resultSet.getString(resultSet.getColumnIndexForName("linkName"));
                String fileId = resultSet.getString(resultSet.getColumnIndexForName("fileId"));
                String fileType = resultSet.getString(resultSet.getColumnIndexForName("fileType"));
                long fileSize = resultSet.getLong(resultSet.getColumnIndexForName("fileSize"));
                long downloadFinishTime = resultSet.getLong(resultSet.getColumnIndexForName("downloadFinishTime"));
                String downloadStatus = resultSet.getString(resultSet.getColumnIndexForName("downloadStatus"));
                HiLog.debug(LOG_LABEL, String.format("downloadId: %d, linkId: %s, linkName: %s, fileId: %s, fileType: %s, " +
                                "fileSize: %d, downloadFinishTime: %d, downloadStatus: %s", downloadId, linkId, linkName, fileId,
                        fileType, fileSize, downloadFinishTime, downloadStatus));
            } while (resultSet.goToNextRow());
        } catch (DataAbilityRemoteException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????????????????DataAbilityRemoteException????????????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        } catch (IllegalStateException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????????????????IllegalStateException????????????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        }
    }

    /**
     * ????????????????????????
     * @param fileLink
     */
    private void insert_fileUpload(FileLink fileLink){
        ValuesBucket values = new ValuesBucket();
        // values.putInteger("uploadId", 0);
        values.putString("linkId", fileLink.getLinkId());
        values.putString("linkName", fileLink.getLinkName());
        values.putString("fileId", fileLink.getFileId());
        values.putString("fileType", fileLink.getFileType());
        values.putLong("fileSize", fileLink.getFileSize());
        values.putLong("uploadFinishTime", new Date().getTime());
        // [TODO] uploadStatus ??????????????????????????????
        values.putString("uploadStatus", "????????????");
        HiLog.debug(LOG_LABEL, fileLink.toString());
        HiLog.debug(LOG_LABEL, values.toString());
        try {
            int result = helper.insert(Uri.parse(UPLOAD_BASE_URI + UPLOAD_DATA_PATH), values);
            if (result != -1){
                HiLog.info(LOG_LABEL, "????????????????????????????????????");
            } else {
                HiLog.warn(LOG_LABEL, "????????????????????????????????????");
            }
        } catch (DataAbilityRemoteException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????????????????DataAbilityRemoteException????????????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        } catch (IllegalStateException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????????????????IllegalStateException????????????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        }
    }

    /**
     * ????????????????????????
     */
    private void query_fileUpload(){
        // ????????????
        String[] columns = new String[] {
                "uploadId", "linkId", "linkName", "fileId", "fileType", "fileSize", "uploadFinishTime", "uploadStatus" };
        // ????????????
        DataAbilityPredicates predicates = new DataAbilityPredicates();
        try {
            ResultSet resultSet = helper.query(Uri.parse(UPLOAD_BASE_URI + UPLOAD_DATA_PATH), columns, predicates);
            if (resultSet == null){
                HiLog.debug(LOG_LABEL, "query: resultSet is null");
                return;
            } else if (resultSet.getRowCount() == 0){
                HiLog.debug(LOG_LABEL, "query: resultSet is no result found");
                return;
            }
            resultSet.goToFirstRow();
            do {
                int uploadId = resultSet.getInt(resultSet.getColumnIndexForName("uploadId"));
                String linkId = resultSet.getString(resultSet.getColumnIndexForName("linkId"));
                String linkName = resultSet.getString(resultSet.getColumnIndexForName("linkName"));
                String fileId = resultSet.getString(resultSet.getColumnIndexForName("fileId"));
                String fileType = resultSet.getString(resultSet.getColumnIndexForName("fileType"));
                long fileSize = resultSet.getLong(resultSet.getColumnIndexForName("fileSize"));
                long uploadFinishTime = resultSet.getLong(resultSet.getColumnIndexForName("uploadFinishTime"));
                String uploadStatus = resultSet.getString(resultSet.getColumnIndexForName("uploadStatus"));
                HiLog.debug(LOG_LABEL, String.format("uploadId: %d, linkId: %s, linkName: %s, fileId: %s, fileType: %s, " +
                        "fileSize: %d, uploadFinishTime: %d, uploadStatus: %s", uploadId, linkId, linkName, fileId,
                        fileType, fileSize, uploadFinishTime, uploadStatus));
            } while (resultSet.goToNextRow());
        } catch (DataAbilityRemoteException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????????????????DataAbilityRemoteException????????????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        } catch (IllegalStateException ex){
            HiLog.error(LOG_LABEL, String.format("??????????????????????????????IllegalStateException????????????????????????%s", ex.getMessage()));
            ex.printStackTrace();
        }
    }

    @Override
    protected void onBackPressed(){
        if (isItemSelected) {
            // ???????????????????????????????????????
            // ????????????????????????
            childItemProvider.cancelSelected();
            childItemProvider.notifyDataChanged();
            setBottomToolBar();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * ?????? ChildItemProvider ??????Callback??????????????? childrenContainer ????????????????????????
     * @param component
     */
    @Override
    public void click(Component component){
        // Toast.makeToast(abilitySlice, "test", Toast.TOAST_SHORT).show();
        switch (component.getId()){
            case ResourceTable.Id_check_layout:
                int position = (Integer) component.getTag();
                // Toast.makeToast(abilitySlice, String.format("??????????????????%d", position), Toast.TOAST_SHORT).show();
                if (childItemProvider.isFolder(position)){
                    // ??????????????????
                    FolderItemInfo folderItemInfo = (FolderItemInfo) childItemProvider.getItem(position);
                    folderItemInfo.setChecked(!folderItemInfo.isChecked());
                    selectedItemCount += folderItemInfo.isChecked() ? 1 : -1;
                } else {
                    // ???????????????
                    FileItemInfo fileItemInfo = (FileItemInfo) childItemProvider.getItem(position);
                    fileItemInfo.setChecked(!fileItemInfo.isChecked());
                    selectedItemCount += fileItemInfo.isChecked() ? 1 : -1;
                }
                childItemProvider.notifyDataSetItemChanged(position);
                if (!isItemSelected){
                    setSelectedBottomToolBar();
                }
                if (selectedItemCount <= 0){
                    setBottomToolBar();
                }
                break;
            default:
                break;
        }
    }

    /**
     * ??????????????????
     */
    private void requestPermissions(){
        if (verifySelfPermission("ohos.permission.WRITE_USER_STORAGE") != IBundleManager.PERMISSION_GRANTED ||
                verifySelfPermission("ohos.permission.CAMERA") != IBundleManager.PERMISSION_GRANTED ||
                verifySelfPermission("ohos.permission.READ_MEDIA") != IBundleManager.PERMISSION_GRANTED ||
                verifySelfPermission("ohos.permission.READ_USER_STORAGE") != IBundleManager.PERMISSION_GRANTED) {

            HiLog.debug(LOG_LABEL, "?????????????????????????????????");
            // ????????????
            if (canRequestPermission("ohos.permission.READ_MEDIA")) {
                // ohos.permission.READ_MEDIA ??????????????????
                HiLog.debug(LOG_LABEL, "ohos.permission.READ_MEDIA ??????????????????");
                requestPermissionsFromUser(
                        new String[]{"ohos.permission.READ_MEDIA",
                                "ohos.permission.WRITE_MEDIA",
                                "ohos.permission.MEDIA_LOCATION",
                                "ohos.permission.CAMERA",
                                "ohos.permission.READ_USER_STORAGE",
                                "ohos.permission.WRITE_USER_STORAGE"
                        }, MY_PERMISSIONS_REQUEST_CAMERA);
            } else {
                // ohos.permission.READ_MEDIA ?????????????????????
                HiLog.warn(LOG_LABEL, "ohos.permission.READ_MEDIA ?????????????????????");
            }
        } else {
            // ?????????
            HiLog.debug(LOG_LABEL, "?????????????????????");
        }
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
















