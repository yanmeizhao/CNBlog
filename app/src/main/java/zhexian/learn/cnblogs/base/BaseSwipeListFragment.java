package zhexian.learn.cnblogs.base;


import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;

import zhexian.learn.cnblogs.R;
import zhexian.learn.cnblogs.ui.PullToRefreshView;
import zhexian.learn.cnblogs.util.Utils;


/**
 * 下拉刷新列表View的基类
 * 提供了下拉刷新、上拉加载数据的功能
 */
public abstract class BaseSwipeListFragment<DataEntity extends BaseEntity> extends Fragment
        implements PullToRefreshView.OnRefreshListener {
    protected BaseApplication mBaseApplication;
    protected BaseActivity mBaseActionBarActivity;
    protected ActionBar mActionBar;

    private PullToRefreshView mPullToRefresh;
    private RecyclerView mRecyclerView;

    private RecyclerView.Adapter<RecyclerView.ViewHolder> mViewAdapter;
    private LinearLayoutManager mLinearLayoutManager;
    private List<DataEntity> mDataList = new ArrayList<>();
    private boolean mIsRequestingData = false;
    private boolean mIsLoadAllData = false;

    /**
     * 绑定列表的数据源
     */
    protected abstract RecyclerView.Adapter<RecyclerView.ViewHolder> bindArrayAdapter(List<DataEntity> list);

    /**
     * 获取数据，具体是从缓存中获取，还是从网络中获取，取决于子类决策
     * 比如新闻类缓存之后一般不变的，博客类的设置缓存时间
     *
     * @param pageIndex 页数，遵循博客园api标准，从1开始
     * @return 数据列表
     */
    protected abstract List<DataEntity> loadData(int pageIndex, int pageSize);

    /**
     * 上拉加载更多,在数组末尾添加载入图标
     */
    protected abstract void onPreLoadMore();


    /**
     * 上拉加载更多，在数组末尾，移除载入图标
     */
    protected abstract void onPostLoadMore();


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mBaseActionBarActivity = (BaseActivity) getActivity();
        mBaseApplication = mBaseActionBarActivity.getApp();
        mActionBar = mBaseActionBarActivity.getSupportActionBar();
        return inflater.inflate(R.layout.base_swipe_list, null);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        mPullToRefresh = (PullToRefreshView) view.findViewById(R.id.base_swipe_container);
        mPullToRefresh.setTextColor(mBaseApplication.isNightMode() ? R.color.green_light : R.color.gray);
        mPullToRefresh.setOnRefreshListener(this);

        mRecyclerView = (RecyclerView) view.findViewById(R.id.base_swipe_list);
        initListView();
    }

    public void initListView() {
        mLinearLayoutManager = new LinearLayoutManager(mBaseActionBarActivity);
        mRecyclerView.setLayoutManager(mLinearLayoutManager);
        ZOnScrollListener scrollListener = new ZOnScrollListener();
        mRecyclerView.setOnScrollListener(scrollListener);

        mViewAdapter = bindArrayAdapter(mDataList);
        mRecyclerView.setAdapter(mViewAdapter);
    }

    @Override
    public void onRefresh() {
        //正在请求数据中就不处理刷新事件了
        if (mIsRequestingData)
            return;

        new AsyncLoadDataTask(true).execute();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mBaseActionBarActivity = null;
        mBaseApplication = null;
        mActionBar = null;
    }

    private int getNextPageIndex() {
        return mDataList.size() / mBaseApplication.getPageSize() + 1;
    }

    private class ZOnScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
        }

        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            mBaseActionBarActivity.switchActionBar(dy);

            if (mIsRequestingData || mIsLoadAllData)
                return;

            if (mDataList.size() == 0)
                return;

            int lastVisibleItem = mLinearLayoutManager.findLastVisibleItemPosition();

            if (lastVisibleItem == mDataList.size() - 1) {
                new AsyncLoadDataTask(false).execute();
            }
        }
    }

    /**
     * 异步载入请求列表的数据
     * 第一个参数，true：刷新列表 false：追加数据
     */
    private class AsyncLoadDataTask extends AsyncTask<Void, Void, List<DataEntity>> {
        boolean isRefresh = false;
        int pageIndex = 1;

        public AsyncLoadDataTask(boolean isRefresh) {
            this.isRefresh = isRefresh;
        }

        @Override
        protected void onPreExecute() {
            mIsRequestingData = true;

            if (isRefresh)
                mPullToRefresh.changeStatus(PullToRefreshView.STATUS_REFRESHING);
            else
                onPreLoadMore();
        }

        @Override
        protected List<DataEntity> doInBackground(Void... params) {
            //activity重建时，提前返回
            if (getActivity() == null) {
                cancel(true);
                return null;
            }

            pageIndex = isRefresh ? 1 : getNextPageIndex();
            return loadData(pageIndex, mBaseApplication.getPageSize());
        }

        @Override
        protected void onPostExecute(List<DataEntity> baseBusinessListEntity) {
            //activity重建时，提前返回
            if (getActivity() == null) {
                cancel(true);
                return;
            }
            mIsRequestingData = false;

            if (baseBusinessListEntity == null) {
                Utils.toast(mBaseApplication, getResources().getString(R.string.load_error));

                if (isRefresh)
                    mPullToRefresh.changeStatus(PullToRefreshView.STATUS_REFRESH_FAIL);
                else
                    onPostLoadMore();

                return;
            }

            if (isRefresh)
                mPullToRefresh.changeStatus(PullToRefreshView.STATUS_REFRESH_SUCCESS);
            else
                onPostLoadMore();

            if (baseBusinessListEntity.size() < mBaseApplication.getPageSize()) {
                mIsLoadAllData = true;
                //页码大于1，则是用户手动触发的加载的，则提示已经加载完毕
                if (pageIndex > 1)
                    Utils.toast(mBaseApplication, getResources().getString(R.string.load_all_load));
            }

            if (isRefresh)
                mDataList.clear();

            mDataList.addAll(baseBusinessListEntity);
            mViewAdapter.notifyDataSetChanged();
        }
    }
}