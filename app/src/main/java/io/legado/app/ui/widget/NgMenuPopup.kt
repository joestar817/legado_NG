package io.legado.app.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

object NgMenuPopup {

    private const val DEFAULT_WIDTH_DP = 0

    private data class NativeOverflowBinding(
        val menu: Menu,
        val candidateIds: List<Int>,
        val prepareMenu: () -> Unit,
        val onItemClick: (MenuItem) -> Unit
    )

    fun bindToolbarMenu(
        context: Context,
        toolbar: Toolbar?,
        menu: Menu,
        prepareMenu: () -> Unit = {},
        onItemClick: (MenuItem) -> Unit
    ) {
        if (toolbar == null) return
        bindActionSubMenus(context, menu, prepareMenu, onItemClick)
        bindOverflowMenu(toolbar, menu, prepareMenu, onItemClick)
    }

    fun show(
        anchor: View,
        menu: Menu,
        widthDp: Int = DEFAULT_WIDTH_DP,
        itemIds: List<Int>? = null,
        includeInvisible: Boolean = false,
        onItemClick: (MenuItem) -> Unit
    ) {
        val items = menu.toPopupItems(
            itemIds = itemIds,
            includeInvisible = includeInvisible
        )
        if (items.isEmpty()) return
        NgActionPopup(anchor.context, items, widthDp) { item ->
            (item.payload as? MenuItem)?.let(onItemClick)
        }.show(anchor)
    }

    fun Menu.toPopupItems(
        itemIds: List<Int>? = null,
        includeInvisible: Boolean = false
    ): List<NgActionPopupItem> {
        val orderedItems = if (itemIds == null) {
            (0 until size()).map { getItem(it) }
        } else {
            itemIds.mapNotNull { id -> findItem(id) }
        }
        var previousGroup = Menu.NONE
        return orderedItems
            .filter { includeInvisible || it.isVisible }
            .mapIndexed { index, item ->
                val groupChanged = index > 0 &&
                    item.groupId != previousGroup &&
                    item.groupId != Menu.NONE
                previousGroup = item.groupId
                val defaultIconRes = item.defaultIconRes()
                val iconRes = if (item.isCheckable && defaultIconRes == R.drawable.ic_check) {
                    0
                } else {
                    defaultIconRes
                }
                NgActionPopupItem(
                    itemId = item.itemId,
                    title = item.title,
                    iconRes = iconRes,
                    iconDrawable = item.icon.takeIf { iconRes == 0 },
                    checked = item.isChecked,
                    dividerBefore = groupChanged,
                    payload = item
                )
            }
    }

    private fun bindActionSubMenus(
        context: Context,
        menu: Menu,
        prepareMenu: () -> Unit,
        onItemClick: (MenuItem) -> Unit
    ) {
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            val subMenu = item.subMenu ?: continue
            if (!item.isVisible || !item.wantsActionButton()) continue
            item.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            item.actionView = createToolbarActionView(
                context = context,
                icon = item.icon,
                iconRes = item.defaultIconRes().takeIf { it != 0 },
                contentDescription = item.title
            ) { anchor ->
                prepareMenu()
                show(anchor, subMenu, onItemClick = onItemClick)
            }
        }
    }

    private fun bindOverflowMenu(
        toolbar: Toolbar,
        menu: Menu,
        prepareMenu: () -> Unit,
        onItemClick: (MenuItem) -> Unit
    ) {
        val candidateIds = overflowCandidates(menu).map { it.itemId }
        if (candidateIds.isEmpty()) return
        bindNativeOverflow(toolbar, menu, candidateIds, prepareMenu, onItemClick)
    }

    private fun overflowCandidates(menu: Menu): List<MenuItem> {
        return (0 until menu.size())
            .map { menu.getItem(it) }
            .filter { item ->
                item.itemId != R.id.menu_more &&
                    item.isVisible &&
                    item.actionView == null &&
                    item.subMenu == null &&
                    !item.wantsActionButton()
            }
    }

    private fun createToolbarActionView(
        context: Context,
        icon: Drawable?,
        @DrawableRes iconRes: Int?,
        contentDescription: CharSequence?,
        onClick: (View) -> Unit
    ): View {
        return ImageButton(context).apply {
            val drawable = icon ?: iconRes?.let { ContextCompat.getDrawable(context, it) }
            setImageDrawable(drawable?.mutate())
            setColorFilter(context.getCompatColor(R.color.primaryText))
            this.contentDescription = contentDescription
            background = toolbarItemBackground(context)
            scaleType = ImageView.ScaleType.CENTER
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick(it) }
            layoutParams = ViewGroup.LayoutParams(48.dpToPx(), 48.dpToPx())
        }
    }

    private fun bindNativeOverflow(
        toolbar: Toolbar,
        menu: Menu,
        candidateIds: List<Int>,
        prepareMenu: () -> Unit,
        onItemClick: (MenuItem) -> Unit
    ) {
        val hasLayoutListener = toolbar.getTag(R.id.menu_more) is NativeOverflowBinding
        toolbar.setTag(
            R.id.menu_more,
            NativeOverflowBinding(
                menu = menu,
                candidateIds = candidateIds,
                prepareMenu = prepareMenu,
                onItemClick = onItemClick
            )
        )
        if (!hasLayoutListener) {
            toolbar.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
                (view as? Toolbar)?.bindNativeOverflowClick()
            }
        }
        toolbar.post { toolbar.bindNativeOverflowClick() }
    }

    private fun Toolbar.bindNativeOverflowClick() {
        val binding = getTag(R.id.menu_more) as? NativeOverflowBinding ?: return
        findActionMenuView()?.findNativeOverflowButton()?.setOnClickListener { anchor ->
            binding.prepareMenu()
            val visibleIds = binding.candidateIds.filter { id ->
                binding.menu.findItem(id)?.isVisible == true
            }
            if (visibleIds.isEmpty()) return@setOnClickListener
            show(
                anchor = anchor,
                menu = binding.menu,
                itemIds = visibleIds,
                onItemClick = binding.onItemClick
            )
        }
    }

    private fun ViewGroup.findActionMenuView(): ActionMenuView? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child is ActionMenuView) return child
            if (child is ViewGroup) {
                child.findActionMenuView()?.let { return it }
            }
        }
        return null
    }

    private fun ActionMenuView.findNativeOverflowButton(): View? {
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.javaClass.name.contains("OverflowMenuButton")) {
                return child
            }
        }
        return null
    }

    private fun toolbarItemBackground(context: Context): Drawable? {
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.actionBarItemBackground, outValue, true)
        return ContextCompat.getDrawable(context, outValue.resourceId)
    }

    private fun MenuItem.wantsActionButton(): Boolean {
        return reflectBoolean("requiresActionButton") || reflectBoolean("requestsActionButton")
    }

    @SuppressLint("PrivateApi")
    private fun MenuItem.reflectBoolean(methodName: String): Boolean {
        return runCatching {
            javaClass.getDeclaredMethod(methodName).apply { isAccessible = true }
                .invoke(this) as? Boolean
        }.getOrNull() == true
    }

    @DrawableRes
    private fun MenuItem.defaultIconRes(): Int {
        return when (itemId) {
            R.id.menu_add,
            R.id.menu_add_book_source,
            R.id.menu_add_replace_rule -> R.drawable.ic_add

            R.id.menu_log -> R.drawable.ic_bug_report
            R.id.menu_network_log -> R.drawable.ic_network_check
            R.id.menu_refresh,
            R.id.menu_web_refresh,
            R.id.menu_rss_refresh,
            R.id.menu_refresh_list,
            R.id.menu_refresh_sort,
            R.id.menu_refresh_explore -> R.drawable.ic_refresh_black_24dp

            R.id.menu_import_local,
            R.id.menu_import_onLine,
            R.id.menu_import_qr,
            R.id.menu_import_default,
            R.id.menu_import -> R.drawable.ic_import

            R.id.menu_help -> R.drawable.ic_help
            R.id.menu_group,
            R.id.menu_group_manage,
            R.id.menu_add_group,
            R.id.menu_remove_group,
            R.id.menu_clear_group,
            R.id.menu_auto_group,
            R.id.menu_book_group,
            R.id.menu_search_scope,
            R.id.menu_group_null,
            R.id.menu_enabled_explore_group,
            R.id.menu_disabled_explore_group,
            R.id.menu_group_sources_by_domain -> R.drawable.ic_groups

            R.id.action_sort,
            R.id.menu_sort,
            R.id.menu_sort_desc,
            R.id.menu_sort_manual,
            R.id.menu_sort_auto,
            R.id.menu_sort_name,
            R.id.menu_sort_url,
            R.id.menu_sort_time,
            R.id.menu_sort_read_long,
            R.id.menu_sort_read_time,
            R.id.menu_sort_respondTime,
            R.id.menu_sort_enable -> R.drawable.ic_sort

            R.id.menu_enabled_group,
            R.id.menu_enable_selection,
            R.id.menu_enable_explore,
            R.id.menu_enable_record,
            R.id.menu_can_update,
            R.id.menu_update_enable -> R.drawable.ic_check

            R.id.menu_disabled_group,
            R.id.menu_disable_selection,
            R.id.menu_disable_explore,
            R.id.menu_disable_source,
            R.id.menu_update_disable -> R.drawable.ic_baseline_close

            R.id.menu_group_login -> R.drawable.ic_lock_outline
            R.id.menu_login -> R.drawable.ic_lock_outline
            R.id.menu_export_selection,
            R.id.menu_export_all,
            R.id.menu_export_bookmark,
            R.id.menu_export_md,
            R.id.menu_export,
            R.id.menu_export_all_use_book_source -> R.drawable.ic_export
            R.id.menu_share_source -> R.drawable.ic_share
            R.id.menu_check_source,
            R.id.menu_check_selected_interval -> R.drawable.ic_check_source

            R.id.menu_top_sel -> R.drawable.ic_expand_more
            R.id.menu_bottom_sel -> R.drawable.ic_expand_more
            R.id.menu_del_selection -> R.drawable.ic_outline_delete
            R.id.menu_top,
            R.id.menu_top_source -> R.drawable.ic_arrow_drop_up
            R.id.menu_bottom,
            R.id.menu_bottom_source -> R.drawable.ic_arrow_down
            R.id.menu_del,
            R.id.menu_delete,
            R.id.menu_delete_source,
            R.id.menu_delete_alert,
            R.id.menu_del_group,
            R.id.menu_del_all,
            R.id.menu_clear,
            R.id.menu_clear_cache -> R.drawable.ic_outline_delete

            R.id.menu_edit,
            R.id.menu_edit_source,
            R.id.menu_edit_content,
            R.id.menu_fullscreen_edit -> R.drawable.ic_edit
            R.id.menu_copy_audio_url,
            R.id.menu_copy_video_url,
            R.id.menu_copy_book_url,
            R.id.menu_copy_toc_url,
            R.id.menu_copy_url,
            R.id.menu_copy_source,
            R.id.menu_copy_rule,
            R.id.menu_copy_content,
            R.id.menu_copy_all -> R.drawable.ic_copy
            R.id.menu_share_it,
            R.id.menu_share_qr,
            R.id.menu_share_str -> R.drawable.ic_share

            R.id.menu_toc_regex,
            R.id.menu_update_toc,
            R.id.menu_catalog,
            R.id.menu_load_toc -> R.drawable.ic_toc
            R.id.menu_split_long_chapter,
            R.id.menu_load_word_count -> R.drawable.ic_chapter_list
            R.id.menu_reverse_toc,
            R.id.menu_reverse_content -> R.drawable.ic_exchange_order
            R.id.menu_use_replace,
            R.id.menu_enable_replace,
            R.id.menu_effective_replaces -> R.drawable.ic_find_replace

            R.id.menu_book_src,
            R.id.menu_toc_src,
            R.id.menu_content_src,
            R.id.menu_search_src,
            R.id.menu_list_src,
            R.id.menu_source_manage -> R.drawable.ic_cfg_source
            R.id.menu_debug_source -> R.drawable.ic_bug_report

            R.id.menu_export_web_dav,
            R.id.menu_upload -> R.drawable.ic_outline_cloud_24
            R.id.menu_export_folder -> R.drawable.ic_folder_open
            R.id.menu_export_pics_file,
            R.id.menu_image_style,
            R.id.menu_manga_color_filter -> R.drawable.ic_image
            R.id.menu_export_charset,
            R.id.menu_set_charset -> R.drawable.ic_translate
            R.id.menu_export_file_name,
            R.id.menu_export_type,
            R.id.menu_enable_custom_export -> R.drawable.ic_custom
            R.id.menu_export_no_chapter_name,
            R.id.menu_parallel_export,
            R.id.menu_re_segment -> R.drawable.ic_arrange

            R.id.menu_add_bookmark,
            R.id.menu_bookmark -> R.drawable.ic_bookmark
            R.id.menu_page_anim,
            R.id.menu_switch_layout -> R.drawable.ic_view_quilt
            R.id.menu_get_progress,
            R.id.menu_cover_progress,
            R.id.menu_read_record -> R.drawable.ic_history
            R.id.menu_simulated_reading,
            R.id.menu_aloud -> R.drawable.ic_read_aloud
            R.id.menu_same_title_removed,
            R.id.menu_del_ruby_tag,
            R.id.menu_del_h_tag -> R.drawable.ic_clear_all

            R.id.menu_open_in_browser,
            R.id.menu_browser_open,
            R.id.menu_browser -> R.drawable.ic_web_outline
            R.id.menu_full_screen -> R.drawable.ic_fullscreen
            R.id.menu_config_settings,
            R.id.menu_server_config -> R.drawable.ic_settings
            R.id.menu_wake_lock -> R.drawable.ic_lock_outline
            R.id.menu_skip_credits -> R.drawable.ic_skip_next
            else -> titleFallbackIconRes()
        }
    }

    @DrawableRes
    private fun MenuItem.titleFallbackIconRes(): Int {
        val text = title?.toString().orEmpty()
        return when {
            text.contains("网络日志") -> R.drawable.ic_network_check
            text.contains("调试日志") || text.contains("日志") -> R.drawable.ic_bug_report
            text.contains("刷新") || text.contains("更新") || text.contains("校验") -> R.drawable.ic_refresh_black_24dp
            text.contains("复制") || text.contains("拷贝") -> R.drawable.ic_copy
            text.contains("删除") || text.contains("清空") -> R.drawable.ic_outline_delete
            text.contains("编辑") -> R.drawable.ic_edit
            text.contains("分享") -> R.drawable.ic_share
            text.contains("导出") -> R.drawable.ic_export
            text.contains("导入") -> R.drawable.ic_import
            text.contains("登录") -> R.drawable.ic_lock_outline
            text.contains("置顶") -> R.drawable.ic_expand_more
            text.contains("置底") -> R.drawable.ic_arrow_down
            text.contains("排序") -> R.drawable.ic_sort
            text.contains("分组") -> R.drawable.ic_groups
            text.contains("目录") -> R.drawable.ic_toc
            text.contains("书签") -> R.drawable.ic_bookmark
            text.contains("替换") -> R.drawable.ic_find_replace
            text.contains("字数") -> R.drawable.ic_chapter_list
            text.contains("反转") -> R.drawable.ic_exchange_order
            text.contains("缓存") -> R.drawable.ic_cache_octicon
            text.contains("上传") -> R.drawable.ic_outline_cloud_24
            text.contains("浏览器") -> R.drawable.ic_web_outline
            text.contains("全屏") -> R.drawable.ic_fullscreen
            text.contains("朗读") -> R.drawable.ic_read_aloud
            text.contains("收藏") -> R.drawable.ic_star_border
            text.contains("启用") || text.contains("允许") -> R.drawable.ic_check
            text.contains("禁用") || text.contains("关闭") -> R.drawable.ic_baseline_close
            else -> 0
        }
    }
}
