package io.legado.app.ui.book.search

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.adapter.DiffRecyclerAdapter
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.data.entities.SearchBook
import io.legado.app.databinding.ItemSearchBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.visible


class SearchAdapter(
    context: Context,
    val callBack: CallBack,
    private val config: Config = Config()
) :
    DiffRecyclerAdapter<SearchBook, ItemSearchBinding>(context) {

    override val keepScrollPosition = true

    override val diffItemCallback: DiffUtil.ItemCallback<SearchBook>
        get() = object : DiffUtil.ItemCallback<SearchBook>() {

            override fun areItemsTheSame(oldItem: SearchBook, newItem: SearchBook): Boolean {
                return when {
                    oldItem.name != newItem.name -> false
                    oldItem.author != newItem.author -> false
                    oldItem.bookUrl.isNotBlank() && newItem.bookUrl.isNotBlank() -> {
                        oldItem.bookUrl == newItem.bookUrl
                    }
                    else -> true
                }
            }

            override fun areContentsTheSame(oldItem: SearchBook, newItem: SearchBook): Boolean {
                return false
            }

            override fun getChangePayload(oldItem: SearchBook, newItem: SearchBook): Any {
                val payload = Bundle()
                payload.putInt("origins", newItem.origins.size)
                if (oldItem.originName != newItem.originName)
                    payload.putString("origin", newItem.originName)
                if (oldItem.coverUrl != newItem.coverUrl)
                    payload.putString("cover", newItem.coverUrl)
                if (oldItem.kind != newItem.kind)
                    payload.putString("kind", newItem.kind)
                if (oldItem.latestChapterTitle != newItem.latestChapterTitle)
                    payload.putString("last", newItem.latestChapterTitle)
                if (oldItem.intro != newItem.intro)
                    payload.putString("intro", newItem.intro)
                return payload
            }

        }

    override fun getViewBinding(parent: ViewGroup): ItemSearchBinding {
        return ItemSearchBinding.inflate(inflater, parent, false).also(::applyConfig)
    }

    override fun convert(
        holder: ItemViewHolder,
        binding: ItemSearchBinding,
        item: SearchBook,
        payloads: MutableList<Any>
    ) {
        if (payloads.isEmpty()) {
            bind(binding, item)
        } else {
            for (i in payloads.indices) {
                val bundle = payloads[i] as Bundle
                bindChange(binding, item, bundle)
            }
        }
    }

    override fun registerListener(holder: ItemViewHolder, binding: ItemSearchBinding) {
        binding.root.setOnClickListener {
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return@setOnClickListener
            getItem(position)?.let {
                callBack.showBookInfo(it.name, it.author, it.bookUrl)
            }
        }
        binding.root.setOnLongClickListener {
            val position = holder.bindingAdapterPosition
            if (position == RecyclerView.NO_POSITION) return@setOnLongClickListener true
            getItem(position)?.let {
                if (config.longClickEnabledProvider?.invoke(it) == false) {
                    return@setOnLongClickListener false
                }
                callBack.showAllSources(it)
            }
            true
        }
    }

    private fun bind(binding: ItemSearchBinding, searchBook: SearchBook) {
        binding.run {
            tvName.text = searchBook.name
            tvAuthor.text = context.getString(R.string.author_show, searchBook.author)
            tvOrigin.text = context.getString(R.string.origin_show, searchBook.originName)
            ivInBookshelf.isVisible = config.showInBookshelf && callBack.isInBookshelf(searchBook)
            val originCount = originCount(searchBook)
            val showOriginCount = showOriginCount(originCount)
            bvOriginCount.isVisible = showOriginCount
            if (showOriginCount) {
                bvOriginCount.setBadgeCount(originCount)
            } else {
                bvOriginCount.setBadgeCount(0)
            }
            upLasted(binding, searchBook.latestChapterTitle)
            tvIntroduce.text = searchBook.trimIntro(context)
            llKind.textSize = 11f
            upKind(binding, searchBook.getKindList())
            ivCover.load(
                searchBook,
                AppConfig.loadCoverOnlyWifi
            )
        }
    }

    private fun bindChange(binding: ItemSearchBinding, searchBook: SearchBook, bundle: Bundle) {
        binding.run {
            bundle.keySet().forEach {
                when (it) {
                    "origins" -> if (config.showOriginCount) {
                        val originCount = originCount(searchBook)
                        val showOriginCount = showOriginCount(originCount)
                        bvOriginCount.isVisible = showOriginCount
                        if (showOriginCount) {
                            bvOriginCount.setBadgeCount(originCount)
                        } else {
                            bvOriginCount.setBadgeCount(0)
                        }
                    }
                    "origin" -> tvOrigin.text =
                        context.getString(R.string.origin_show, searchBook.originName)
                    "last" -> upLasted(binding, searchBook.latestChapterTitle)
                    "intro" -> tvIntroduce.text = searchBook.trimIntro(context)
                    "kind" -> upKind(binding, searchBook.getKindList())
                    "isInBookshelf" -> ivInBookshelf.isVisible =
                        config.showInBookshelf && callBack.isInBookshelf(searchBook)
                    "cover" -> ivCover.load(
                        searchBook,
                        false
                    )
                }
            }
        }
    }

    private fun upLasted(binding: ItemSearchBinding, latestChapterTitle: String?) {
        binding.run {
            if (latestChapterTitle.isNullOrEmpty()) {
                tvLasted.gone()
            } else {
                tvLasted.text =
                    context.getString(R.string.lasted_show, latestChapterTitle)
                tvLasted.visible()
            }
        }
    }

    private fun upKind(binding: ItemSearchBinding, kinds: List<String>) = binding.run {
        if (kinds.isEmpty()) {
            llKind.gone()
        } else {
            llKind.visible()
            llKind.setLabels(kinds)
        }
    }

    private fun applyConfig(binding: ItemSearchBinding) {
        config.horizontalMarginDp?.let { marginDp ->
            val margin = marginDp.dpToPx()
            val params = binding.root.layoutParams as? ViewGroup.MarginLayoutParams ?: return@let
            params.marginStart = margin
            params.marginEnd = margin
            binding.root.layoutParams = params
        }
        binding.bvOriginCount.isVisible = config.showOriginCount
        config.backgroundRes?.let {
            binding.root.setBackgroundResource(it)
        }
    }

    private fun originCount(searchBook: SearchBook): Int {
        return config.originCountProvider?.invoke(searchBook) ?: searchBook.origins.size
    }

    private fun showOriginCount(originCount: Int): Boolean {
        return config.showOriginCount && originCount >= config.minOriginCount
    }

    data class Config(
        val showOriginCount: Boolean = true,
        val horizontalMarginDp: Int? = null,
        val backgroundRes: Int? = null,
        val showInBookshelf: Boolean = true,
        val minOriginCount: Int = 1,
        val originCountProvider: ((SearchBook) -> Int)? = null,
        val longClickEnabledProvider: ((SearchBook) -> Boolean)? = null
    )

    interface CallBack {

        /**
         * 是否已经加入书架
         */
        fun isInBookshelf(book: SearchBook): Boolean

        /**
         * 显示书籍详情
         */
        fun showBookInfo(name: String, author: String, bookUrl: String)

        /**
         * 显示当前书籍的所有来源
         */
        fun showAllSources(book: SearchBook)
    }
}
