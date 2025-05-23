package ani.himitsu.media

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.text.HtmlCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.R
import ani.himitsu.connections.Status
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.GenresViewModel
import ani.himitsu.copyToClipboard
import ani.himitsu.currActivity
import ani.himitsu.databinding.ActivityGenreBinding
import ani.himitsu.databinding.FragmentMediaInfoBinding
import ani.himitsu.databinding.ItemChipBinding
import ani.himitsu.databinding.ItemQuelsBinding
import ani.himitsu.databinding.ItemTitleChipgroupBinding
import ani.himitsu.databinding.ItemTitleRecyclerBinding
import ani.himitsu.databinding.ItemTitleTextBinding
import ani.himitsu.displayTimer
import ani.himitsu.isOffline
import ani.himitsu.loadImage
import ani.himitsu.profile.User
import ani.himitsu.setSafeOnClickListener
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import bit.himitsu.content.dpToColumns
import bit.himitsu.content.toPx
import bit.himitsu.nio.totalEpisodeText
import bit.himitsu.nio.utf8
import bit.himitsu.os.Version
import io.noties.markwon.Markwon
import io.noties.markwon.SoftBreakAddsNewLinePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withUIContext
import java.io.Serializable

class MediaInfoFragment : Fragment() {
    private var _binding: FragmentMediaInfoBinding? = null
    private val binding by lazy { _binding!! }
    private var timer: CountDownTimer? = null
    private var loaded = false
    private var type = "ANIME"
    private val genreModel: GenresViewModel by activityViewModels()

    private val tripleTab = "\t\t\t"

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMediaInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView();_binding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val model: MediaDetailsViewModel by activityViewModels()
        val offline: Boolean = requireContext().isOffline
        binding.mediaInfoProgressBar.isGone = loaded
        binding.mediaInfoContainer.isVisible = loaded

        model.scrolledToTop.observe(viewLifecycleOwner) {
            if (it) binding.mediaInfoScroll.scrollTo(0, 0)
        }

        model.getMedia().observe(viewLifecycleOwner) { media ->
            if (media != null && !loaded) {
                loaded = true

                binding.mediaInfoProgressBar.visibility = View.GONE
                binding.mediaInfoContainer.visibility = View.VISIBLE

                binding.mediaInfoMeanScore.text =
                    if (media.meanScore != null) (media.meanScore / 10.0).toString() else "??"
                binding.mediaInfoStatus.text = media.status
                binding.mediaInfoFormat.text = media.format
                binding.mediaInfoSource.text = media.source
                binding.mediaInfoStart.text = media.startDate?.toString() ?: "??"
                binding.mediaInfoEnd.text = media.endDate?.toString() ?: "??"
                binding.mediaInfoPopularity.text = media.popularity.toString()
                binding.mediaInfoFavorites.text = media.favourites.toString()
                if (media.anime != null) {
                    val episodeDuration = media.anime.episodeDuration

                    binding.mediaInfoDuration.text = when {
                        episodeDuration != null -> {
                            val hours = episodeDuration / 60
                            val minutes = episodeDuration % 60

                            val formattedDuration = buildString {
                                if (hours > 0) {
                                    append("$hours hour")
                                    if (hours > 1) append("s")
                                }

                                if (minutes > 0) {
                                    if (hours > 0) append(", ")
                                    append("$minutes min")
                                    if (minutes > 1) append("s")
                                }
                            }

                            formattedDuration
                        }

                        else -> "??"
                    }
                    binding.mediaInfoDurationContainer.visibility = View.VISIBLE
                    binding.mediaInfoSeasonContainer.visibility = View.VISIBLE
                    val seasonInfo =
                        "${(media.anime.season ?: "??")} ${(media.anime.seasonYear ?: "??")}"
                    binding.mediaInfoSeason.text = seasonInfo

                    if (media.anime.mainStudio != null) {
                        binding.mediaInfoStudioContainer.visibility = View.VISIBLE
                        binding.mediaInfoStudio.text = media.anime.mainStudio!!.name
                        if (!offline) {
                            binding.mediaInfoStudioContainer.setOnClickListener {
                                requireActivity().startActivity(
                                    Intent(activity, StudioActivity::class.java).putExtra(
                                        "studio",
                                        media.anime.mainStudio!! as Serializable
                                    )
                                )
                            }
                        }
                    }
                    if (media.anime.author != null) {
                        binding.mediaInfoAuthorContainer.visibility = View.VISIBLE
                        binding.mediaInfoAuthor.text = media.anime.author!!.name
                        if (!offline) {
                            binding.mediaInfoAuthorContainer.setOnClickListener {
                                requireActivity().startActivity(
                                    Intent(activity, AuthorActivity::class.java).putExtra(
                                        "author",
                                        media.anime.author!! as Serializable
                                    )
                                )
                            }
                        }
                    }
                    binding.mediaInfoTotalTitle.setText(R.string.total_eps)
                    binding.mediaInfoTotal.text = media.anime.totalEpisodeText

                } else if (media.manga != null) {
                    type = "MANGA"
                    binding.mediaInfoTotalTitle.setText(R.string.total_chaps)
                    val infoTotal = "${media.manga.totalChapters ?: "??"}".let {
                        if (media.status == Status.FINISHED) it else "[${it}]"
                    }
                    binding.mediaInfoTotal.text = infoTotal
                    if (media.manga.author != null) {
                        binding.mediaInfoAuthorContainer.visibility = View.VISIBLE
                        binding.mediaInfoAuthor.text = media.manga.author!!.name
                        if (!offline) {
                            binding.mediaInfoAuthorContainer.setOnClickListener {
                                requireActivity().startActivity(
                                    Intent(activity, AuthorActivity::class.java).putExtra(
                                        "author",
                                        media.manga.author!! as Serializable
                                    )
                                )
                            }
                        }
                    }
                }

                val desc = HtmlCompat.fromHtml(
                    (media.description ?: "null").replace("\\n", "<br>").replace("\\\"", "\""),
                    HtmlCompat.FROM_HTML_MODE_LEGACY
                )
                val infoDesc =
                    tripleTab + if (desc.toString() != "null") desc else getString(R.string.no_description_available)
                binding.mediaInfoDescription.text = infoDesc

                binding.mediaInfoDescription.setOnClickListener {
                    if (binding.mediaInfoDescription.maxLines == 5) {
                        ObjectAnimator.ofInt(binding.mediaInfoDescription, "maxLines", 100)
                            .setDuration(950).start()
                    } else {
                        ObjectAnimator.ofInt(binding.mediaInfoDescription, "maxLines", 5)
                            .setDuration(400).start()
                    }
                }

                binding.searchLayoutItem.apply {
                    titleSearchImage.loadImage(media.banner ?: media.cover)
                    titleSearchText.text =
                        getString(R.string.search_title, media.mainName())
                    titleSearchCard.setSafeOnClickListener {
                        requireContext().startActivity(
                            Intent(requireContext(), SearchActivity::class.java)
                            .putExtra("type", "ANIME")
                            .putExtra("query", media.mainName())
                            .putExtra("hideKeyboard", true)
                        )
                    }
                }

                binding.mediaInfoContainer.displayTimer(media)
                val parent = _binding?.mediaInfoContainer!!

                val synonyms = arrayListOf(media.nameRomaji)
                synonyms.addAll(media.synonyms)
                val baseName = media.name ?: media.nameMAL
                baseName?.let {
                    synonyms.add(0, it)
                }
                if (synonyms.isNotEmpty()) {
                    val bind = ItemTitleChipgroupBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    )
                    for (position in synonyms.indices) {
                        val chip = ItemChipBinding.inflate(
                            LayoutInflater.from(context),
                            bind.itemChipGroup,
                            false
                        ).root
                        chip.text = synonyms[position]
                        chip.setOnLongClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            copyToClipboard(synonyms[position])
                            true
                        }
                        bind.itemChipGroup.addView(chip)
                    }
                    parent.addView(bind.root)
                }

//                if (!media.review.isNullOrEmpty()) {
//                    ItemTitleRecyclerBinding.inflate(
//                        LayoutInflater.from(context),
//                        parent,
//                        false
//                    ).apply {
//                        fun onUserClick(userId: Int) {
//                            val review = media.review!!.find { i -> i.id == userId }
//                            if (review != null) {
//                                startActivity(
//                                    Intent(requireContext(), ReviewViewActivity::class.java)
//                                        .putExtra("review", review)
//                                )
//                            }
//                        }
//                        val adapter = GroupieAdapter()
//                        media.review!!.forEach {
//                            adapter.add(ReviewAdapter(it, ::onUserClick))
//                        }
//                        itemTitle.setText(R.string.reviews)
//                        itemRecycler.adapter = adapter
//                        itemRecycler.layoutManager = LinearLayoutManager(
//                            requireContext(),
//                            LinearLayoutManager.VERTICAL,
//                            false
//                        )
//                        itemMore.visibility = View.VISIBLE
//                        itemMore.setSafeOnClickListener {
//                            startActivity(
//                                Intent(requireContext(), ReviewActivity::class.java)
//                                    .putExtra("mediaId", media.id)
//                            )
//                        }
//                        parent.addView(root)
//                    }
//                }

                if ((media.sequel != null || media.prequel != null) && !offline) {
                    ItemQuelsBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    ).apply {

                        if (media.sequel != null) {
                            mediaInfoSequel.visibility = View.VISIBLE
                            mediaInfoSequelImage.loadImage(
                                media.sequel!!.banner ?: media.sequel!!.cover
                            )
                            mediaInfoSequel.setSafeOnClickListener {
                                // MediaSingleton.bitmap = mediaInfoSequelImage.toBitmap()
                                requireContext().startActivity(
                                    Intent(
                                        requireContext(),
                                        MediaDetailsActivity::class.java
                                    ).putExtra(
                                        "media", media.sequel as Serializable
                                    )
                                )
                            }
                        }
                        if (media.prequel != null) {
                            mediaInfoPrequel.visibility = View.VISIBLE
                            mediaInfoPrequelImage.loadImage(
                                media.prequel!!.banner ?: media.prequel!!.cover
                            )
                            mediaInfoPrequel.setSafeOnClickListener {
                                // MediaSingleton.bitmap = mediaInfoPrequelImage.toBitmap()
                                requireContext().startActivity(
                                    Intent(
                                        requireContext(),
                                        MediaDetailsActivity::class.java
                                    ).putExtra(
                                        "media", media.prequel as Serializable
                                    )
                                )
                            }
                        }
                        parent.addView(root)
                    }
                }

                if (!media.characters.isNullOrEmpty() && !offline) {
                    var characterPage = 1
                    ItemTitleRecyclerBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    ).apply {
                        itemTitle.setText(R.string.characters)
                        itemRecycler.adapter =
                            CharacterAdapter(media.characters!!)
                        itemRecycler.layoutManager = LinearLayoutManager(
                            requireContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        if (media.characterPages > 1) {
                            itemMore.isVisible = true
                            itemMore.setOnClickListener {
                                itemMore.isEnabled = false
                                if (characterPage < media.characterPages) {
                                    characterPage += 1
                                    lifecycleScope.launch(Dispatchers.IO) {
                                        AniList.query.getCharacterPage(media, characterPage).characters?.let {
                                            withUIContext {
                                                itemRecycler.adapter = CharacterAdapter(it)
                                                itemRecycler.scrollToPosition((characterPage - 1) * 25)
                                                itemMore.isEnabled = true
                                                itemMore.isVisible = characterPage != media.characterPages
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        parent.addView(root)
                    }
                }

                if (media.anime != null && (media.anime.op.isNotEmpty() || media.anime.ed.isNotEmpty()) && !offline) {
                    val markWon = Markwon.builder(requireContext())
                        .usePlugin(SoftBreakAddsNewLinePlugin.create()).build()

                    fun makeLink(a: String): String {
                        val first = a.indexOf('"').let { if (it != -1) it else return a } + 1
                        val end = a.indexOf('"', first).let { if (it != -1) it else return a }
                        val name = a.subSequence(first, end).toString()
                        return "${a.subSequence(0, first)}" +
                                "[$name](https://www.youtube.com/results?search_query=${name.utf8})" +
                                "${a.subSequence(end, a.length)}"
                    }

                    fun makeText(textView: TextView, arr: ArrayList<String>) {
                        var op = ""
                        arr.forEach {
                            op += "\n"
                            op += makeLink(it)
                        }
                        op = op.removePrefix("\n")
                        textView.setOnClickListener {
                            if (textView.maxLines == 4) {
                                ObjectAnimator.ofInt(textView, "maxLines", 100)
                                    .setDuration(950).start()
                            } else {
                                ObjectAnimator.ofInt(textView, "maxLines", 4)
                                    .setDuration(400).start()
                            }
                        }
                        markWon.setMarkdown(textView, op)
                    }

                    if (media.anime.op.isNotEmpty()) {
                        val bind = ItemTitleTextBinding.inflate(
                            LayoutInflater.from(context),
                            parent,
                            false
                        )
                        bind.itemTitle.setText(R.string.opening)
                        makeText(bind.itemText, media.anime.op)
                        parent.addView(bind.root)
                    }


                    if (media.anime.ed.isNotEmpty()) {
                        val bind = ItemTitleTextBinding.inflate(
                            LayoutInflater.from(context),
                            parent,
                            false
                        )
                        bind.itemTitle.setText(R.string.ending)
                        makeText(bind.itemText, media.anime.ed)
                        parent.addView(bind.root)
                    }
                }

                if (!media.staff.isNullOrEmpty() && !offline) {
                    ItemTitleRecyclerBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    ).apply {
                        itemTitle.setText(R.string.staff)
                        itemRecycler.adapter =
                            AuthorAdapter(media.staff!!)
                        itemRecycler.layoutManager = LinearLayoutManager(
                            requireContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        parent.addView(root)
                    }
                }

                if (media.genres.isNotEmpty() && !offline) {
                    val bind = ActivityGenreBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    )
                    bind.listBackButton.visibility = View.GONE
                    bind.listTitle.updatePadding(left = 32.toPx, top = 8.toPx)
                    val adapter = GenreAdapter(type)
                    genreModel.doneListener = {
                        MainScope().launch {
                            bind.mediaInfoGenresProgressBar.visibility = View.GONE
                        }
                    }
                    if (genreModel.genres != null) {
                        adapter.genres = genreModel.genres!!
                        adapter.pos = ArrayList(genreModel.genres!!.keys)
                        if (genreModel.done) genreModel.doneListener?.invoke()
                    }
                    bind.mediaInfoGenresRecyclerView.updatePadding(left = 4.toPx)
                    bind.mediaInfoGenresRecyclerView.adapter = adapter
                    bind.mediaInfoGenresRecyclerView.layoutManager = GridLayoutManager(
                        requireActivity(), 156.dpToColumns
                    )

                    lifecycleScope.launch(Dispatchers.IO) {
                        genreModel.loadGenres(media.genres) {
                            MainScope().launch {
                                adapter.addGenre(it)
                            }
                        }
                    }
                    parent.addView(bind.root)
                }

                if (media.tags.isNotEmpty() && !offline) {
                    val bind = ItemTitleChipgroupBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    )
                    bind.itemTitle.setText(R.string.tags)
                    for (position in media.tags.indices) {
                        val chip = ItemChipBinding.inflate(
                            LayoutInflater.from(context),
                            bind.itemChipGroup,
                            false
                        ).root
                        chip.text = media.tags[position]
                        chip.setSafeOnClickListener {
                            chip.context.startActivity(
                                Intent(chip.context, SearchActivity::class.java)
                                    .putExtra("type", type)
                                    .putExtra("sortBy", AniList.sortBy[2])
                                    .putExtra("tag", media.tags[position].substringBefore(" :"))
                                    .putExtra("hideKeyboard", true)
                                    .also {
                                        if (media.isAdult) {
                                            if (!AniList.adult) Toast.makeText(
                                                chip.context,
                                                currActivity()?.getString(R.string.content_18),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            it.putExtra("hentai", true)
                                        }
                                    }
                            )
                        }
                        chip.setOnLongClickListener {
                            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            copyToClipboard(media.tags[position])
                            true
                        }
                        bind.itemChipGroup.addView(chip)
                    }
                    parent.addView(bind.root)
                }

                if (PrefManager.getVal(PrefName.SocialInMedia) && AniList.userid != null) {
                    if (!media.users.isNullOrEmpty() && !offline) {
                        val users: ArrayList<User> = media.users ?: arrayListOf()
                        if (media.userStatus != null) {
                            users.add(
                                0,
                                User(
                                    id = AniList.userid!!,
                                    name = getString(R.string.you),
                                    pfp = AniList.avatar,
                                    banner = "",
                                    status = media.userStatus,
                                    score = media.userScore.toFloat(),
                                    progress = media.userProgress,
                                    totalEpisodes = media.anime?.totalEpisodes
                                        ?: media.manga?.totalChapters,
                                    nextAiringEpisode = media.anime?.nextAiringEpisode
                                )
                            )
                        }
                        ItemTitleRecyclerBinding.inflate(
                            LayoutInflater.from(context),
                            parent,
                            false
                        ).apply {
                            itemTitle.visibility = View.GONE
                            itemRecycler.adapter =
                                MediaSocialAdapter(users, type, requireActivity())
                            itemRecycler.layoutManager = LinearLayoutManager(
                                requireContext(),
                                LinearLayoutManager.HORIZONTAL,
                                false
                            )
                            parent.addView(root)
                        }
                    }
                }

                if (!media.recommendations.isNullOrEmpty() && !offline) {
                    ItemTitleRecyclerBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    ).apply {
                        itemTitle.setText(R.string.recommended)
                        itemRecycler.adapter =
                            MediaAdaptor(ViewType.COMPACT, media.recommendations!!, requireActivity())
                        itemRecycler.layoutManager = LinearLayoutManager(
                            requireContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        parent.addView(root)
                    }
                }

                if (!media.relations.isNullOrEmpty() && !offline) {
                    ItemTitleRecyclerBinding.inflate(
                        LayoutInflater.from(context),
                        parent,
                        false
                    ).apply {

                        itemRecycler.adapter =
                            MediaAdaptor(ViewType.COMPACT, media.relations!!, requireActivity())
                        itemRecycler.layoutManager = LinearLayoutManager(
                            requireContext(),
                            LinearLayoutManager.HORIZONTAL,
                            false
                        )
                        parent.addView(root)
                    }
                }
            }
        }

        if (Version.isMarshmallow) {
            val cornerTop = ObjectAnimator.ofFloat(binding.root, "radius", 0f, 32f).setDuration(200)
            val cornerNotTop =
                ObjectAnimator.ofFloat(binding.root, "radius", 32f, 0f).setDuration(200)
            var cornered = true
            cornerTop.start()
            binding.mediaInfoScroll.setOnScrollChangeListener { v, _, _, _, _ ->
                if (!v.canScrollVertically(-1)) {
                    if (!cornered) {
                        cornered = true
                        cornerTop.start()
                    }
                } else {
                    if (cornered) {
                        cornered = false
                        cornerNotTop.start()
                    }
                }
            }
        }

        super.onViewCreated(view, null)
    }

    override fun onResume() {
        binding.mediaInfoProgressBar.isGone = loaded
        super.onResume()
    }

    override fun onDestroy() {
        timer?.cancel()
        super.onDestroy()
    }
}
