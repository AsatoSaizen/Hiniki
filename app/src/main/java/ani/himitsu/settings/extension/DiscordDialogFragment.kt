package ani.himitsu.settings.extension

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import ani.himitsu.R
import ani.himitsu.connections.discord.Discord
import ani.himitsu.connections.discord.RPC
import ani.himitsu.databinding.BottomSheetDiscordRpcBinding
import ani.himitsu.loadGif
import ani.himitsu.settings.saving.PrefManager
import ani.himitsu.settings.saving.PrefName
import ani.himitsu.view.dialog.BottomSheetDialogFragment

class DiscordDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetDiscordRpcBinding? = null
    private val binding by lazy { _binding!! }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetDiscordRpcBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        when (PrefManager.getCustomVal("discord_mode", Discord.MODE.HIMITSU.name)) {
            Discord.MODE.NOTHING.name -> binding.radioNothing.isChecked = true
            Discord.MODE.HIMITSU.name -> binding.radioHimitsu.isChecked = true
            Discord.MODE.ANILIST.name -> binding.radioAniList.isChecked = true
            else -> binding.radioAniList.isChecked = true
        }
        binding.loopIcon.isChecked = PrefManager.getVal(PrefName.LoopAnimatedRPC)
        binding.iconDemo.loadGif(
            if (binding.loopIcon.isChecked) RPC.loopHimitsu else RPC.drawnHimitsu,
            !binding.loopIcon.isChecked
        )
        binding.loopIcon.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.LoopAnimatedRPC, isChecked)
            binding.iconDemo.loadGif(
                if (isChecked) RPC.loopHimitsu else RPC.drawnHimitsu, !isChecked
            )
        }
        binding.showIcon.isChecked = PrefManager.getVal(PrefName.ShowAniListIcon)
        binding.showIcon.setOnCheckedChangeListener { _, isChecked ->
            PrefManager.setVal(PrefName.ShowAniListIcon, isChecked)
        }
        val username = PrefManager.getVal<String>(PrefName.AnilistUserName)
        binding.aniListCardText.text = getString(R.string.rpc_anilist, username)
        binding.aniListLinkPreview.text = getString(R.string.anilist_link, username)

        binding.radioGroup.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                binding.radioNothing.id -> Discord.MODE.NOTHING.name
                binding.radioHimitsu.id -> Discord.MODE.HIMITSU.name
                binding.radioAniList.id -> Discord.MODE.ANILIST.name
                else -> Discord.MODE.HIMITSU.name
            }
            PrefManager.setCustomVal("discord_mode", mode)
        }
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}