package io.github.ascenderx.mobilescript.ui.shortcuts

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.github.ascenderx.mobilescript.R


class ShortcutFragment : Fragment() {

    companion object {
        fun newInstance() =
            ShortcutFragment()
    }

    private lateinit var viewModel: ShortcutViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_shortcut, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        viewModel = ViewModelProviders.of(this).get(ShortcutViewModel::class.java)
        // TODO: Use the ViewModel
    }

}
