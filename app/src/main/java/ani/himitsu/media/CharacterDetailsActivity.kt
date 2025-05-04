package ani.himitsu.media

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.math.MathUtils.clamp
import androidx.core.view.isGone
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import ani.himitsu.R
import ani.himitsu.Refresh
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.AniListMutations
import ani.himitsu.databinding.ActivityCharacterBinding
import ani.himitsu.initActivity
import ani.himitsu.loadImage
import ani.himitsu.media.cereal.Character
import ani.himitsu.navBarHeight
import ani.himitsu.openLinkInBrowser
import ani.himitsu.others.getSerialized
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import ani.himitsu.toast
import ani.himitsu.view.dialog.ImageViewDialog
import bit.himitsu.content.dpToColumns
import bit.himitsu.content.toPx
import bit.himitsu.setStatusColor
import bit.himitsu.setStatusTransparent
import com.google.android.material.appbar.AppBarLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.util.lang.withIOContext
import tachiyomi.core.util.lang.withUIContext
import kotlin.math.abs

class CharacterDetailsActivity : AppCompatActivity(), AppBarLayout.OnOffsetChangedListener {
    private lateinit var binding: ActivityCharacterBinding
    private val scope = lifecycleScope
    private val model: OtherDetailsViewModel by viewModels()
    private lateinit var character: Character
    private var loaded = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        ThemeManager(this).applyTheme()
        binding = ActivityCharacterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initActivity(this)

        if (PrefManager.getVal(PrefName.ImmersiveMode)) window.setStatusTransparent()

        val banner =
            if (PrefManager.getVal(PrefName.BannerAnimations)) binding.characterBanner else binding.characterBannerNoKen

        banner.updateLayoutParams { height += statusBarHeight }
        binding.characterClose.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        binding.characterCollapsing.minimumHeight = statusBarHeight
        binding.characterCover.updateLayoutParams<ViewGroup.MarginLayoutParams> { topMargin += statusBarHeight }
        // TODO: Investigate hardcoded values
        binding.characterRecyclerView.updatePadding(bottom = 64.toPx + navBarHeight)
        binding.characterTitle.isSelected = true
        binding.characterAppBar.addOnOffsetChangedListener(this)

        binding.characterClose.setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        character = intent.getSerialized("character") ?: return
        binding.characterTitle.text = character.name
        banner.loadImage(character.banner)
        binding.characterCoverImage.loadImage(character.image)
        binding.characterCoverImage.setOnLongClickListener {
            ImageViewDialog.newInstance(
                this,
                character.name,
                character.image
            )
        }
        val link = "https://anilist.co/character/${character.id}"
        binding.characterShare.setOnClickListener {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, link)
            startActivity(Intent.createChooser(i, character.name))
        }
        binding.characterShare.setOnLongClickListener {
            openLinkInBrowser(link)
            true
        }
        lifecycleScope.launch {
            withIOContext {
                character.isFav =
                    AniList.query.isUserFav(AniListMutations.FavType.CHARACTER, character.id)
            }
            withUIContext {
                binding.characterFav.setImageResource(
                    if (character.isFav) R.drawable.round_favorite_24 else R.drawable.round_favorite_border_24
                )
            }
        }
        binding.characterFav.setOnClickListener {
            lifecycleScope.launch {
                if (AniList.mutation.toggleFav(AniListMutations.FavType.CHARACTER, character.id)) {
                    character.isFav = !character.isFav
                    binding.characterFav.setImageResource(
                        if (character.isFav) R.drawable.round_favorite_24 else R.drawable.round_favorite_border_24
                    )
                } else {
                    toast("Failed to toggle favorite")
                }
            }
        }
        model.getCharacter().observe(this) {
            if (it != null && !loaded) {
                character = it
                loaded = true
                binding.characterProgress.visibility = View.GONE
                binding.characterRecyclerView.visibility = View.VISIBLE

                val roles = character.roles
                if (roles != null) {
                    val mediaAdaptor = MediaAdaptor(ViewType.COMPACT, roles, this, matchParent = true)
                    val concatAdaptor =
                        ConcatAdapter(CharacterDetailsAdapter(character, this), mediaAdaptor)

                    val gridSize = 124.dpToColumns
                    val gridLayoutManager = GridLayoutManager(this, gridSize)
                    gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                        override fun getSpanSize(position: Int): Int {
                            return when (position) {
                                0 -> gridSize
                                else -> 1
                            }
                        }
                    }
                    binding.characterRecyclerView.adapter = concatAdaptor
                    binding.characterRecyclerView.layoutManager = gridLayoutManager
                }
            }
        }

        val live = Refresh.activity.getOrPut(this.hashCode()) { MutableLiveData(true) }
        live.observe(this) {
            scope.launch(Dispatchers.IO) {
                model.loadCharacter(character)
            }
        }
    }

    override fun onResume() {
        binding.characterProgress.isGone = loaded
        super.onResume()
    }

    private var isCollapsed = false
    private val percent = 30
    private var mMaxScrollSize = 0

    override fun onOffsetChanged(appBar: AppBarLayout, i: Int) {
        if (mMaxScrollSize == 0) mMaxScrollSize = appBar.totalScrollRange
        val percentage = abs(i) * 100 / mMaxScrollSize
        val cap = clamp((percent - percentage) / percent.toFloat(), 0f, 1f)

        binding.characterCover.scaleX = 1f * cap
        binding.characterCover.scaleY = 1f * cap
        binding.characterCover.cardElevation = 32f * cap

        binding.characterCover.visibility =
            if (binding.characterCover.scaleX == 0f) View.GONE else View.VISIBLE
        val immersiveMode: Boolean = PrefManager.getVal(PrefName.ImmersiveMode)
        if (percentage >= percent && !isCollapsed) {
            isCollapsed = true
            if (immersiveMode) window.setStatusColor( R.color.nav_bg)
        }
        if (percentage <= percent && isCollapsed) {
            isCollapsed = false
            if (immersiveMode) window.setStatusTransparent()
        }
    }
}