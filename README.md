# MediaSDK

关注一下分析文章：https://www.jianshu.com/p/27085da32a35

```
allprojects {
  repositories {
    ...
    maven { url 'https://jitpack.io' }
  }
}

dependencies {
        implementation 'com.github.JeffMony:MediaSDK:2.0.0'
}
```

最近这个项目有新的维护计划：
> * 1.本地代理的控制逻辑移到server端
> * 2.增加mp4 moov端的识别规则
> * 3.将本地代理库和播放器解耦

### 版本LOG
2.0.0
> * 1.使用androidasync替换proxyserver
> *  2.优化MediaSDK接口

t1.5.0
> * 1.视频下载队列，可以设置视频并发下载的个数
> * 2.视频播放缓存和下载缓存的数据合并，但是逻辑分离

t1.4.0
> * 1.增加视频下载模块;
> * 2.重构本地代理模块代码;
> * 3.视频下载和本地代理模块代码复用;
> * 4.还有一些bug待处理,很快更新
> * 5.后续版本更新计划: 下载队列；初始化本地已下载的视频；下载和播放缓存隔离；

t1.3.0
> * 1.封装好边下边播模块
> * 2.可以直接商用

v1.1.0
> * 1.解决https 证书出错的视频url请求，信任证书；
> * 2.解决播放过程中息屏的问题，保持屏幕常亮；
> * 3.增加 isPlaying 接口，表示当前是否正在播放视频；
> * 4.解决Cleartext HTTP traffic to 127.0.0.1 not permitted 问题，Android P版本不允许未加密请求；

v1.0.0
> * 1.支持MediaPlayer/IjkPlayer/ExoPlayer 三种播放器播放视频；
> * 2.支持M3U8/MP4视频的边下边播功能；
> * 3.本地代理实现边下边播功能；
> * 4.提供当前下载速度和下载进度的回调；


#### 封装了一个播放器功能库
> * 实现ijkplayer  exoplayer mediaplayer 3种播放器类型；可以任意切换；
> * ijkplayer 是从 k0.8.8分支上拉出来的；
> * exoplayer 是 2.11.1版本
#### 实现视频边下边播的功能
> * 缓存管理
> * 下载管理
> * 本地代理管理模块(使用androidasync管理本地代理)
> * 回调播放下载实时速度
> * 显示缓存大小

本项目的架构如下：
![](./files/LocalProxy.png)
从上面的架构可以看出来，本项目的重点在本地代理层，这是实现边下边播的核心逻辑；


### 接入库须知
#### 1.在Application->onCreate(...) 中初始化
```
File file = LocalProxyUtils.getVideoCacheDir(this);
if (!file.exists()) {
    file.mkdir();
}
LocalProxyConfig config = new VideoDownloadManager.Build(this)
    .setCacheRoot(file)
    .setUrlRedirect(false)
    .setTimeOut(DownloadConstants.READ_TIMEOUT, DownloadConstants.CONN_TIMEOUT, DownloadConstants.SOCKET_TIMEOUT)
    .setConcurrentCount(DownloadConstants.CONCURRENT_COUNT)
    .setIgnoreAllCertErrors(true)
    .buildConfig();
VideoDownloadManager.getInstance().initConfig(config);
```
这儿可以设置一些属性：
1.setCacheRoot            设置缓存的路径；
2.setUrlRedirect          是否需要重定向请求；
3.setCacheSize            设置缓存的大小限制；
4.setTimeOut              设置连接和读超时时间；
5.setPort                 设置本地代理的端口；
6.setIgnoreAllCertErrors  是否需要信任证书；
#### 2.打开本地代理开关
```
PlayerAttributes attributes = new PlayerAttributes();
attributes.setUseLocalProxy(mUseLocalProxy);
```
#### 3.设置本地代理模块监听
```
mPlayer.setOnLocalProxyCacheListener(mOnLocalProxyCacheListener);
mPlayer.startLocalProxy(mUrl, null);

private IPlayer.OnLocalProxyCacheListener mOnLocalProxyCacheListener = new IPlayer.OnLocalProxyCacheListener() {
    @Override
    public void onCacheReady(IPlayer mp, String proxyUrl) {
        LogUtils.w("onCacheReady proxyUrl = " + proxyUrl);
        Uri uri = Uri.parse(proxyUrl);
        try {
            mPlayer.setDataSource(PlayerActivity.this, uri);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        mPlayer.setSurface(mSurface);
        mPlayer.setOnPreparedListener(mPreparedListener);
        mPlayer.setOnVideoSizeChangedListener(mVideoSizeChangeListener);
        mPlayer.prepareAsync();
    }

    @Override
    public void onCacheProgressChanged(IPlayer mp, int percent, long cachedSize) {
        LogUtils.w("onCacheProgressChanged percent = " + percent);
        mPercent = percent;
    }

    @Override
    public void onCacheSpeedChanged(String url, float cacheSpeed) {
        if (mPlayer != null && mPlayer.get() != null) {
            mPlayer.get().notifyProxyCacheSpeed(cacheSpeed);
        }
    }

    @Override
    public void onCacheFinished(String url) {
        LogUtils.i("onCacheFinished url="+url + ", player="+this);
        mIsCompleteCached = true;
    }

    @Override
    public void onCacheForbidden(String url) {
        LogUtils.w("onCacheForbidden url="+url+", player="+this);
        mUseLocalProxy = false;
        if (mPlayer != null && mPlayer.get() != null) {
            mPlayer.get().notifyProxyCacheForbidden(url);
        }
    }

    @Override
    public void onCacheFailed(String url, Exception e) {
        LogUtils.w("onCacheFailed , player="+this);
        pauseProxyCacheTask(PROXY_CACHE_EXCEPTION);
    }
};
```
#### 4.本地代理的生命周期跟着播放器的生命周期一起
#### 5.下载接入函数

```
public interface IDownloadListener {

    void onDownloadPrepare(VideoTaskItem item);

    void onDownloadPending(VideoTaskItem item);

    void onDownloadStart(VideoTaskItem item);

    void onDownloadProxyReady(VideoTaskItem item);

    void onDownloadProgress(VideoTaskItem item);

    void onDownloadSpeed(VideoTaskItem item);

    void onDownloadPause(VideoTaskItem item);

    void onDownloadError(VideoTaskItem item);

    void onDownloadProxyForbidden(VideoTaskItem item);

    void onDownloadSuccess(VideoTaskItem item);
}
```


### 功能概要
#### 1.封装了一个player sdk层
> * 1.1 接入Android 原生的 MediaPlayer 播放器
> * 1.2 接入google的EXO player 播放器
> * 1.3 接入开源的 ijk player 播放器
#### 2.实现整视频的边下边播
> * 2.1 实现整视频的分片下载
> * 2.2 实现整视频的断点下载
#### 3.实现HLS分片视频的边下边播
> * 3.1 实现HLS视频源的解析工作
> * 3.2 实现HLS的边下边播
> * 3.3 实现HLS的断点下载功能
#### 4.线程池控制下载功能
#### 5.提供视频下载的额外功能
> * 5.1 可以提供播放视频或者下载视频的实时网速
> * 5.2 可以提供已缓存视频的大小

demo示意图：<br>
![](./files/test1_low.jpg)![](./files/test2_low.jpg)

欢迎关注我的公众号JeffMony，我会持续为你带来音视频---算法---Android---python 方面的知识分享<br>
![](./files/JeffMony.jpg)

如果你觉得这个库有用,请鼓励一下吧;<br>
![](./files/ErWeiMa.jpg)
