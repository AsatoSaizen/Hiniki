package ani.himitsu.profile

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.text.SpannableString
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.connections.anilist.AniList
import ani.himitsu.connections.anilist.api.User
import ani.himitsu.databinding.ActivityFollowBinding
import ani.himitsu.initActivity
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.statusBarHeight
import ani.himitsu.themes.ThemeManager
import bit.himitsu.content.dpToColumns
import bit.himitsu.setBaseline
import com.xwray.groupie.GroupieAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tachiyomi.core.util.lang.withUIContext


class FollowActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFollowBinding
    val adapter = GroupieAdapter()
    var users: List<User>? = null
    private lateinit var selected: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeManager(this).applyTheme()
        initActivity(this)
        binding = ActivityFollowBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.listToolbar.updateLayoutParams<MarginLayoutParams> { topMargin = statusBarHeight }
        binding.listRecyclerView.setBaseline()
        val layoutType = PrefManager.getVal<Int>(PrefName.FollowerLayout)
        selected = getSelected(layoutType)
        binding.followFilterButton.visibility = View.GONE
        binding.followerGrid.alpha = 0.33f
        binding.followerList.alpha = 0.33f
        selected(selected)
        binding.listRecyclerView.layoutManager = LinearLayoutManager(
            this,
            LinearLayoutManager.VERTICAL,
            false
        )
        binding.listRecyclerView.adapter = adapter
        binding.listProgressBar.visibility = View.VISIBLE
        binding.listBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val title = intent.getStringExtra("title")
        val userID = intent.getIntExtra("userId", 0)
        binding.listTitle.text = title

        lifecycleScope.launch(Dispatchers.IO) {
            val respond = when (title) {
                "Following" -> AniList.query.userFollowing(userID)?.data?.page?.following
                "Followers" -> AniList.query.userFollowers(userID)?.data?.page?.followers
                else -> null
            }
            users = respond
            withUIContext {
                fillList()
                binding.listProgressBar.visibility = View.GONE
            }
        }
        binding.followerList.setOnClickListener {
            selected(it as ImageButton)
            PrefManager.setVal(PrefName.FollowerLayout, 0)
            fillList()
        }
        binding.followerGrid.setOnClickListener {
            selected(it as ImageButton)
            PrefManager.setVal(PrefName.FollowerLayout, 1)
            fillList()
        }
        binding.followSwipeRefresh.setOnRefreshListener {
            binding.followSwipeRefresh.isRefreshing = false
        }
    }

    private fun fillList() {
        adapter.clear()
        binding.listRecyclerView.layoutManager = when (getLayoutType(selected)) {
            0 -> LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
            1 -> GridLayoutManager(this, 120.dpToColumns, GridLayoutManager.VERTICAL, false)
            else -> LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false)
        }
        users?.forEach { user ->
            if (getLayoutType(selected) == 0) {
                val username = SpannableString(user.name ?: "Unknown")
                adapter.add(
                    FollowerItem(
                        user.id,
                        username,
                        user.avatar?.medium,
                        user.bannerImage ?: user.avatar?.medium
                    ) { onUserClick(it) })
            } else {
                adapter.add(
                    GridFollowerItem(
                        user.id,
                        user.name ?: "Unknown",
                        user.avatar?.medium
                    ) { onUserClick(it) })
            }
        }
    }

    fun selected(it: ImageButton) {
        selected.alpha = 0.33f
        selected = it
        selected.alpha = 1f
    }

    private fun getSelected(pos: Int): ImageButton {
        return when (pos) {
            0 -> binding.followerList
            1 -> binding.followerGrid
            else -> binding.followerList
        }
    }

    private fun getLayoutType(it: ImageButton): Int {
        return when (it) {
            binding.followerList -> 0
            binding.followerGrid -> 1
            else -> 0
        }
    }

    private fun onUserClick(id: Int) {
        val intent = Intent(this, ProfileActivity::class.java)
        intent.putExtra("userId", id)
        startActivity(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        binding.listRecyclerView.setBaseline(newConfig)
    }
}