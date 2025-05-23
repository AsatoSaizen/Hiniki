package ani.himitsu.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import ani.himitsu.databinding.BottomSheetUsersBinding
import ani.himitsu.view.dialog.BottomSheetDialogFragment


class UsersDialogFragment : BottomSheetDialogFragment() {
    private var _binding: BottomSheetUsersBinding? = null
    private val binding by lazy { _binding!! }

    private var userList = arrayListOf<User>()
    fun userList(user: ArrayList<User>) {
        userList = user
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetUsersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.usersRecyclerView.adapter = UsersAdapter(userList)
        binding.usersRecyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    override fun onDestroy() {
        _binding = null
        super.onDestroy()
    }
}