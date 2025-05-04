package ani.himitsu.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.LayoutAnimationController
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ani.himitsu.R
import ani.himitsu.connections.anilist.ProfileViewModel
import ani.himitsu.connections.anilist.UrlMedia
import ani.himitsu.connections.anilist.api.Query
import ani.himitsu.databinding.FragmentProfileBinding
import ani.himitsu.media.AuthorAdapter
import ani.himitsu.media.CharacterAdapter
import ani.himitsu.media.MediaAdaptor
import ani.himitsu.media.ViewType
import ani.himitsu.media.cereal.Author
import ani.himitsu.media.cereal.Character
import ani.himitsu.media.cereal.Media
import ani.himitsu.openLinkInBrowser
import ani.himitsu.setSlideIn
import ani.himitsu.setSlideUp
import ani.himitsu.util.AniMarkdown.getFullAniHTML
import bit.himitsu.nio.string
import eu.kanade.tachiyomi.util.system.getSerializableCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class ProfileFragment : Fragment() {
    lateinit var binding: FragmentProfileBinding
    private lateinit var activity: ProfileActivity
    private lateinit var user: Query.UserProfile
    private val favStaff = arrayListOf<Author>()
    private val favCharacter = arrayListOf<Character>()
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    val model: ProfileViewModel by activityViewModels()
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        activity = requireActivity() as ProfileActivity

        user = arguments?.getSerializableCompat<Query.UserProfile>("user") as Query.UserProfile
        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            model.setData(user.id)
        }
        binding.profileUserBio.settings.loadWithOverviewMode = true
        binding.profileUserBio.settings.useWideViewPort = true
        val styledHtml = getFullAniHTML(
            user.about ?: "",
            ContextCompat.getColor(activity, R.color.bg_opp)
        )
        binding.profileUserBio.loadDataWithBaseURL(
            null, styledHtml, "text/html", "utf-8", null
        )
        binding.profileUserBio.setBackgroundColor(
            ContextCompat.getColor(
                activity,
                android.R.color.transparent
            )
        )
        // binding.profileUserBio.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        binding.profileUserBio.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.profileUserBio.setBackgroundColor(
                    ContextCompat.getColor(
                        activity,
                        android.R.color.transparent
                    )
                )
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                request?.url?.toString()?.let { url ->
                    if (url.contains("anilist") || url.contains("myanimelist")) {
                        Intent(activity, UrlMedia::class.java).apply { data = request.url }
                    } else {
                        openLinkInBrowser(url)
                    }
                }
                return true
            }
        }

        binding.userInfoContainer.isVisible = user.about != null

        binding.statsEpisodesWatched.text = user.statistics.anime.episodesWatched.string
        binding.statsDaysWatched.text = (user.statistics.anime.minutesWatched / (24 * 60)).string
        binding.statsAnimeMeanScore.text = user.statistics.anime.meanScore.string
        binding.statsChaptersRead.text = user.statistics.manga.chaptersRead.string
        binding.statsVolumeRead.text = (user.statistics.manga.volumesRead).string
        binding.statsMangaMeanScore.text = user.statistics.manga.meanScore.string
        initRecyclerView(
            model.getAnimeFav(),
            binding.profileFavAnimeContainer,
            binding.profileFavAnimeRecyclerView,
            binding.profileFavAnimeProgressBar,
            binding.profileFavAnime
        )

        initRecyclerView(
            model.getMangaFav(),
            binding.profileFavMangaContainer,
            binding.profileFavMangaRecyclerView,
            binding.profileFavMangaProgressBar,
            binding.profileFavManga
        )

        user.favourites?.characters?.nodes?.forEach { i ->
            favCharacter.add(Character(i.id, i.name.full, i.image.large, i.image.large, "", true))
        }

        user.favourites?.staff?.nodes?.forEach { i ->
            favStaff.add(Author(i.id, i.name.full, i.image.large, ""))
        }

        setFavPeople()
    }

    private fun setFavPeople() {
        if (favStaff.isEmpty()) {
            binding.profileFavStaffContainer.visibility = View.GONE
        } else {
            binding.profileFavStaffRecycler.adapter = AuthorAdapter(favStaff)
            binding.profileFavStaffRecycler.layoutManager = LinearLayoutManager(
                activity, LinearLayoutManager.HORIZONTAL, false
            )
            binding.profileFavStaffRecycler.layoutAnimation =
                LayoutAnimationController(setSlideIn(), 0.25f)
        }

        if (favCharacter.isEmpty()) {
            binding.profileFavCharactersContainer.visibility = View.GONE
        } else {
            binding.profileFavCharactersRecycler.adapter = CharacterAdapter(favCharacter)
            binding.profileFavCharactersRecycler.layoutManager = LinearLayoutManager(
                activity, LinearLayoutManager.HORIZONTAL, false
            )
            binding.profileFavCharactersRecycler.layoutAnimation =
                LayoutAnimationController(setSlideIn(), 0.25f)
        }
    }

    private fun initRecyclerView(
        mode: LiveData<ArrayList<Media>>,
        container: View,
        recyclerView: RecyclerView,
        progress: View,
        title: View
    ) {
        container.visibility = View.VISIBLE
        progress.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        title.visibility = View.INVISIBLE

        mode.observe(viewLifecycleOwner) {
            recyclerView.visibility = View.GONE
            if (it != null) {
                if (it.isNotEmpty()) {
                    recyclerView.adapter = MediaAdaptor(ViewType.COMPACT, it, activity, fav = true)
                    recyclerView.layoutManager = LinearLayoutManager(
                        activity,
                        LinearLayoutManager.HORIZONTAL,
                        false
                    )
                    recyclerView.visibility = View.VISIBLE
                    recyclerView.layoutAnimation =
                        LayoutAnimationController(setSlideIn(), 0.25f)

                } else {
                    container.visibility = View.GONE
                }
                title.visibility = View.VISIBLE
                title.startAnimation(setSlideUp())
                progress.visibility = View.GONE
            }
        }
    }

    companion object {
        fun newInstance(query: Query.UserProfile): ProfileFragment {
            val args = Bundle().apply {
                putSerializable("user", query)
            }
            return ProfileFragment().apply {
                arguments = args
            }
        }
    }

}