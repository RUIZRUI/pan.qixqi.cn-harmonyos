package cn.qixqi.pan.slice;

import cn.qixqi.pan.MyApplication;
import cn.qixqi.pan.ResourceTable;
import cn.qixqi.pan.dao.TokenDao;
import cn.qixqi.pan.dao.impl.TokenDaoImpl;
import cn.qixqi.pan.datamodel.BottomBarItemInfo;
import cn.qixqi.pan.datamodel.FileItemInfo;
import cn.qixqi.pan.datamodel.FolderItemInfo;
import cn.qixqi.pan.model.FolderLink;
import cn.qixqi.pan.util.ElementUtil;
import cn.qixqi.pan.util.HttpUtil;
import cn.qixqi.pan.util.Toast;
import cn.qixqi.pan.view.BottomBarItemView;
import cn.qixqi.pan.view.FileItemView;
import cn.qixqi.pan.view.FolderItemView;
import cn.qixqi.pan.view.adapter.FileItemProvider;
import cn.qixqi.pan.view.adapter.FolderItemProvider;
import com.alibaba.fastjson.JSON;
import ohos.aafwk.ability.AbilitySlice;
import ohos.aafwk.content.Intent;
import ohos.agp.components.*;
import ohos.agp.utils.Color;
import ohos.app.dispatcher.TaskDispatcher;
import ohos.hiviewdfx.HiLog;
import ohos.hiviewdfx.HiLogLabel;
import okhttp3.Call;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

public class FileSystemAbilitySlice extends AbilitySlice {

    private static final HiLogLabel LOG_LABEL = new HiLogLabel(3, 0xD001100, FileSystemAbilitySlice.class.getName());
    // ListContainer 弹性回滚效果参数
    private static final int OVER_SCROLL_PERCENT = 40;
    private static final float OVER_SCROLL_RATE = 0.6f;
    private static final int REMAIN_VISIBLE_PERCENT = 20;

    private TokenDao tokenDao;
    private FolderLink folderLink;
    private AbilitySlice abilitySlice;

    private static final String GET_FOLDER_LINK_URL = "http://ali4.qixqi.cn:5555/api/filesystem/v1/filesystem/folderLink";

    private ListContainer foldersContainer;
    private ListContainer filesContainer;
    private FileItemProvider fileItemProvider;
    private FolderItemProvider folderItemProvider;

    private Text title;
    private DirectionalLayout downloadItemLayout;
    private DirectionalLayout uploadItemLayout;

    private List<BottomBarItemInfo> bottomBarItemInfoList;

    @Override
    public void onStart(Intent intent) {
        super.onStart(intent);
        super.setUIContent(ResourceTable.Layout_ability_file_system);

        abilitySlice = this;

        this.getWindow().setStatusBarColor(ElementUtil.getColor(this, ResourceTable.Color_colorSubBackground));
        this.getWindow().setNavigationBarColor(ElementUtil.getColor(this, ResourceTable.Color_colorSubBackground));

        tokenDao = new TokenDaoImpl(MyApplication.getAppContext());
        initView();
        initListener();

        // 设置底部导航栏
        setBottomToolBar();

        getFolderLink();
    }

    /**
     * 初始化控件和布局
     */
    private void initView(){
        foldersContainer = (ListContainer) findComponentById(ResourceTable.Id_folders_container);
        filesContainer = (ListContainer) findComponentById(ResourceTable.Id_files_container);
        // **********标题栏*************
        title = (Text) findComponentById(ResourceTable.Id_title);
        title.setText(ResourceTable.String_file_title);
        downloadItemLayout = (DirectionalLayout) findComponentById(ResourceTable.Id_download_item_layout);
        uploadItemLayout = (DirectionalLayout) findComponentById(ResourceTable.Id_upload_item_layout);
    }

    /**
     * 初始化监听器
     */
    private void initListener(){
        // **********标题栏*************
        // 标题文本点击事件
        title.setClickedListener( component -> {
            Toast.makeToast(abilitySlice, "点击标题", Toast.TOAST_SHORT).show();
        });
        // downloadItemLayout 点击事件
        downloadItemLayout.setClickedListener(component -> {
            Toast.makeToast(abilitySlice, "点击downloadItemLayout", Toast.TOAST_SHORT).show();
        });
        // 点击 uploadItemLayout
        uploadItemLayout.setClickedListener( component -> {
            Toast.makeToast(abilitySlice, "点击uploadItemLayout", Toast.TOAST_SHORT).show();
        });
    }

    /**
     * 获取文件夹列表和文件列表后，设置 ListContainer
     */
    private void setListContainer(){
        if (folderLink == null){
            HiLog.warn(LOG_LABEL, "folderLink 为空");
            return;
        }
        FolderItemView folderItemView = new FolderItemView(folderLink.getChildren().getFolders());
        folderItemProvider = new FolderItemProvider(folderItemView.getFolderItemInfos());
        FileItemView fileItemView = new FileItemView(folderLink.getChildren().getFiles());
        fileItemProvider = new FileItemProvider(fileItemView.getFileItemInfos());

        foldersContainer.setItemProvider(folderItemProvider);
        filesContainer.setItemProvider(fileItemProvider);

        // 设置 ListContainer 的事件监听器
        setListClickListener();

        // 设置文件列表回滚动画
        setListReboundAnimation();
    }

    /**
     * 设置 ListContainer 的事件监听器
     */
    private void setListClickListener(){
        // foldersContainer 子项单击事件
        foldersContainer.setItemClickedListener( (listContainer, component, position, id) -> {
            FolderItemInfo folderItemInfo = (FolderItemInfo) folderItemProvider.getItem(position);
            Toast.makeToast(abilitySlice, folderItemInfo.toString(), Toast.TOAST_SHORT).show();
        });

        // filesContainer 子项单击事件
        filesContainer.setItemClickedListener( (listContainer, component, position, id) -> {
            FileItemInfo fileItemInfo = (FileItemInfo) fileItemProvider.getItem(position);
            Toast.makeToast(abilitySlice, fileItemInfo.toString(), Toast.TOAST_SHORT).show();
        });
    }

    /**
     * 设置文件列表回滚动画
     */
    private void setListReboundAnimation() {
        foldersContainer.setReboundEffect(true);
        foldersContainer.setReboundEffectParams(OVER_SCROLL_PERCENT, OVER_SCROLL_RATE, REMAIN_VISIBLE_PERCENT);

        filesContainer.setReboundEffect(true);
        filesContainer.setReboundEffectParams(OVER_SCROLL_PERCENT, OVER_SCROLL_RATE, REMAIN_VISIBLE_PERCENT);
    }

    /**
     * 获取当前文件夹
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

                    // 设置 ListContainer
                    // 在主线程(UI线程)中执行
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
     * 设置底部导航栏
     */
    private void setBottomToolBar(){
        BottomBarItemView bottomBarItemView = new BottomBarItemView();
        bottomBarItemInfoList = bottomBarItemView.getBottomBarItemInfos();

        IntStream.range(0, bottomBarItemInfoList.size()).forEach( position -> {
            DirectionalLayout bottomItemLayout = (DirectionalLayout) abilitySlice.findComponentById(
                    bottomBarItemInfoList.get(position).getBnavLayoutId());
            bottomItemLayout.setVisibility(Component.VISIBLE);
            Image image = (Image) bottomItemLayout.findComponentById(
                    bottomBarItemInfoList.get(position).getBnavImgId());
            Text text = (Text) bottomItemLayout.findComponentById(
                    bottomBarItemInfoList.get(position).getBnavTextId());
            if (position == 0){
                // 设为选中
                image.setImageAndDecodeBounds(
                        bottomBarItemInfoList.get(position).getBnavActivatedImgSrcId());
                text.setTextColor(Color.BLUE);
            } else {
                image.setImageAndDecodeBounds(
                        bottomBarItemInfoList.get(position).getBnavImgSrcId());
            }

            // 设置子项点击事件
            bottomItemLayout.setClickedListener( component -> {
                unselected();
                HiLog.debug(LOG_LABEL, "设置子项点击事件：" + position);
                image.setImageAndDecodeBounds(
                        bottomBarItemInfoList.get(position).getBnavActivatedImgSrcId());
                text.setTextColor(Color.BLUE);
            });
        });
    }

    /**
     * 底部导航栏全部子项取消选中
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

    @Override
    public void onActive() {
        super.onActive();
    }

    @Override
    public void onForeground(Intent intent) {
        super.onForeground(intent);
    }
}
