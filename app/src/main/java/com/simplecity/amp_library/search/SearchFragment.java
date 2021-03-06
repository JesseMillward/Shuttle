package com.simplecity.amp_library.search;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.util.Pair;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.SearchView;
import android.support.v7.widget.Toolbar;
import android.transition.Transition;
import android.transition.TransitionInflater;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.jakewharton.rxbinding2.support.v7.widget.RxSearchView;
import com.simplecity.amp_library.R;
import com.simplecity.amp_library.ShuttleApplication;
import com.simplecity.amp_library.format.PrefixHighlighter;
import com.simplecity.amp_library.model.Album;
import com.simplecity.amp_library.model.AlbumArtist;
import com.simplecity.amp_library.model.Song;
import com.simplecity.amp_library.tagger.TaggerDialog;
import com.simplecity.amp_library.ui.detail.AlbumDetailFragment;
import com.simplecity.amp_library.ui.detail.ArtistDetailFragment;
import com.simplecity.amp_library.ui.detail.BaseDetailFragment;
import com.simplecity.amp_library.ui.dialog.DeleteDialog;
import com.simplecity.amp_library.ui.dialog.UpgradeDialog;
import com.simplecity.amp_library.ui.fragments.BaseFragment;
import com.simplecity.amp_library.ui.modelviews.EmptyView;
import com.simplecity.amp_library.ui.modelviews.LoadingView;
import com.simplecity.amp_library.ui.modelviews.SelectableViewModel;
import com.simplecity.amp_library.ui.views.ContextualToolbar;
import com.simplecity.amp_library.ui.views.ContextualToolbarHost;
import com.simplecity.amp_library.utils.ContextualToolbarHelper;
import com.simplecity.amp_library.utils.MenuUtils;
import com.simplecity.amp_library.utils.Operators;
import com.simplecity.amp_library.utils.PlaylistUtils;
import com.simplecity.amp_library.utils.ResourceUtils;
import com.simplecityapps.recycler_adapter.adapter.ViewModelAdapter;
import com.simplecityapps.recycler_adapter.model.ViewModel;
import com.simplecityapps.recyclerview_fastscroll.views.FastScrollRecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Single;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;

public class SearchFragment extends BaseFragment implements
        com.simplecity.amp_library.search.SearchView,
        ContextualToolbarHost {

    private static final String TAG = "SearchFragment";

    public static final String ARG_QUERY = "query";

    private String query;

    @BindView(R.id.toolbar)
    Toolbar toolbar;

    @BindView(R.id.contextualToolbar)
    ContextualToolbar contextualToolbar;

    @BindView(R.id.recyclerView)
    FastScrollRecyclerView recyclerView;

    ViewModelAdapter adapter;

    private LoadingView loadingView;

    private EmptyView emptyView;

    private CompositeDisposable disposables = new CompositeDisposable();

    SearchPresenter searchPresenter;

    View rootView;

    SearchView searchView;

    private ContextualToolbarHelper<Single<List<Song>>> contextualToolbarHelper;

    public static SearchFragment newInstance(String query) {
        Bundle args = new Bundle();
        args.putString(ARG_QUERY, query);
        SearchFragment fragment = new SearchFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @SuppressLint("InlinedApi")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        searchPresenter = new SearchPresenter(new PrefixHighlighter(), Glide.with(this));

        query = getArguments().getString(ARG_QUERY);

        loadingView = new LoadingView();

        emptyView = new EmptyView(R.string.empty_search);
        emptyView.setHeight(ResourceUtils.toPixels(96));

        adapter = new ViewModelAdapter();
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {

        rootView = inflater.inflate(R.layout.fragment_search, container, false);

        ButterKnife.bind(this, rootView);

        toolbar.inflateMenu(R.menu.menu_search);
        toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.search_fuzzy:
                    item.setChecked(!item.isChecked());
                    searchPresenter.setSearchFuzzy(item.isChecked());
                    break;
                case R.id.search_artist:
                    item.setChecked(!item.isChecked());
                    searchPresenter.setSearchArtists(item.isChecked());
                    break;
                case R.id.search_album:
                    item.setChecked(!item.isChecked());
                    searchPresenter.setSearchAlbums(item.isChecked());
                    break;
            }
            return false;
        });

        setupContextualToolbar();

        MenuItem searchItem = toolbar.getMenu().findItem(R.id.search);
        searchItem.expandActionView();
        searchView = (SearchView) searchItem.getActionView();

        searchItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                return false;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                inputMethodManager.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
                searchView.getHandler().postDelayed(() -> getNavigationController().popViewController(), 150);
                return false;
            }
        });

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();

        searchPresenter.bindView(this);

        disposables.add(RxSearchView.queryTextChangeEvents(searchView)
                .skip(1)
                .debounce(200, TimeUnit.MILLISECONDS)
                .toFlowable(BackpressureStrategy.LATEST)
                .subscribe(searchViewQueryTextEvent -> {
                    query = searchViewQueryTextEvent.queryText().toString();
                    searchPresenter.queryChanged(query);
                }));

        searchPresenter.queryChanged(query);
    }

    @Override
    public void onPause() {
        disposables.clear();
        searchPresenter.unbindView(this);

        super.onPause();
    }

    @Override
    protected String screenName() {
        return TAG;
    }

    @Override
    public void setLoading(boolean loading) {
        adapter.setItems(Collections.singletonList(loadingView));
    }

    @Override
    public void setEmpty(boolean empty) {
        adapter.setItems(Collections.singletonList(emptyView));
    }

    @Override
    public Disposable setItems(@NonNull List<ViewModel> items) {
        Disposable disposable = adapter.setItems(items);
        recyclerView.scrollToPosition(0);
        return disposable;
    }

    @Override
    public void setFilterFuzzyChecked(boolean checked) {
        toolbar.getMenu().findItem(R.id.search_fuzzy).setChecked(checked);
    }

    @Override
    public void setFilterArtistsChecked(boolean checked) {
        toolbar.getMenu().findItem(R.id.search_artist).setChecked(checked);
    }

    @Override
    public void setFilterAlbumsChecked(boolean checked) {
        toolbar.getMenu().findItem(R.id.search_album).setChecked(checked);
    }

    @Override
    public void showToast(String message) {
        Toast.makeText(getContext(), (ShuttleApplication.getInstance().getString(R.string.emptyplaylist)), Toast.LENGTH_SHORT).show();
    }

    @Override
    public void showTaggerDialog(@NonNull TaggerDialog taggerDialog) {
        taggerDialog.show(getChildFragmentManager());
    }

    @Override
    public void showDeleteDialog(@NonNull DeleteDialog deleteDialog) {
        deleteDialog.show(getChildFragmentManager());
    }

    @Override
    public void goToArtist(AlbumArtist albumArtist, View transitionView) {
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
        String transitionName = ViewCompat.getTransitionName(transitionView);
        searchView.getHandler().postDelayed(() -> pushDetailFragment(ArtistDetailFragment.newInstance(albumArtist, transitionName), transitionView), 50);
    }

    @Override
    public void goToAlbum(Album album, View transitionView) {
        InputMethodManager inputMethodManager = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow(rootView.getWindowToken(), 0);
        String transitionName = ViewCompat.getTransitionName(transitionView);
        searchView.getHandler().postDelayed(() -> pushDetailFragment(AlbumDetailFragment.newInstance(album, transitionName), transitionView), 50);
    }

    @Override
    public void showUpgradeDialog() {
        UpgradeDialog.getUpgradeDialog(getActivity()).show();
    }

    void pushDetailFragment(BaseDetailFragment detailFragment, @Nullable View transitionView) {

        List<Pair<View, String>> transitions = new ArrayList<>();

        if (transitionView != null) {
            String transitionName = ViewCompat.getTransitionName(transitionView);
            transitions.add(new Pair<>(transitionView, transitionName));

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                Transition moveTransition = TransitionInflater.from(getContext()).inflateTransition(R.transition.image_transition);
                detailFragment.setSharedElementEnterTransition(moveTransition);
                detailFragment.setSharedElementReturnTransition(moveTransition);
            }
        }

        getNavigationController().pushViewController(detailFragment, "DetailFragment", transitions);
    }

    @Override
    public ContextualToolbar getContextualToolbar() {
        return contextualToolbar;
    }

    private void setupContextualToolbar() {

        ContextualToolbar contextualToolbar = ContextualToolbar.findContextualToolbar(this);
        if (contextualToolbar != null) {

            contextualToolbar.getMenu().clear();
            contextualToolbar.inflateMenu(R.menu.context_menu_general);
            SubMenu sub = contextualToolbar.getMenu().findItem(R.id.addToPlaylist).getSubMenu();
            disposables.add(PlaylistUtils.createUpdatingPlaylistMenu(sub).subscribe());

            contextualToolbar.setOnMenuItemClickListener(MenuUtils.getSongMenuClickListener(
                    getContext(),
                    Single.defer(() -> Operators.reduceSongSingles(contextualToolbarHelper.getItems())),
                    deleteDialog -> deleteDialog.show(getChildFragmentManager())
                    , () -> contextualToolbarHelper.finish()));

            contextualToolbarHelper = new ContextualToolbarHelper<Single<List<Song>>>(contextualToolbar, new ContextualToolbarHelper.Callback() {

                @Override
                public void notifyItemChanged(int position, SelectableViewModel viewModel) {
                    adapter.notifyItemChanged(position, 0);
                }

                @Override
                public void notifyDatasetChanged() {
                    adapter.notifyItemRangeChanged(0, adapter.items.size(), 0);
                }
            }) {
                @Override
                public void start() {
                    super.start();

                    toolbar.setVisibility(View.GONE);
                }

                @Override
                public void finish() {
                    super.finish();

                    toolbar.setVisibility(View.VISIBLE);
                }
            };
            searchPresenter.setContextualToolbarHelper(contextualToolbarHelper);
        }
    }
}
