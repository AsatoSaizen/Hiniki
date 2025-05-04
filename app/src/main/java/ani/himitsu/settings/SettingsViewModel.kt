package ani.himitsu.settings

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import ani.himitsu.R
import bit.himitsu.nio.Strings.getString
import java.util.Locale

class SettingsViewModel : ViewModel() {
    private val query = MutableLiveData<String?>(null)

    fun getQuery(): LiveData<String?> = query

    fun setQuery(queryData: String?) { query.postValue(queryData) }

    val uiResources: ArrayList<Int> = arrayListOf(
        R.string.startUpTab,
        R.string.home_layout_show,
        R.string.immersive_mode,
        R.string.hide_home_main,
        R.string.hide_home_main_desc,
        R.string.random_recommended,
        R.string.random_recommended_desc,
        R.string.show_forum_button,
        R.string.show_anibrain_button,
        R.string.load_affinity,
        R.string.small_view,
        R.string.small_view_scale,
        R.string.persist_search,
        R.string.use_foldable,
        R.string.use_foldable_desc,
        R.string.floating_avatar,
        R.string.trailer_banners,
        R.string.banner_animations,
        R.string.layout_animations,
        R.string.trending_scroller,
        R.string.animation_speed,
        R.string.blur_banners,
        R.string.radius,
        R.string.sampling
    )

    val themeResources: ArrayList<Int> = arrayListOf(
        R.string.day_night,
        R.string.oled_theme_variant,
        R.string.oled_theme_variant_desc,
        R.string.use_material_you,
        R.string.use_material_you_desc,
        R.string.theme_use_media,
        R.string.theme_use_media_desc,
        R.string.theme_use_profile,
        R.string.theme_use_profile_desc,
        R.string.use_custom_theme,
        R.string.use_custom_theme_desc,
        R.string.color_picker,
        R.string.color_picker_desc
    )

    val commonResources: ArrayList<Int> = arrayListOf(
        R.string.always_continue_content,
        R.string.always_continue_content_desc,
        R.string.add_shortcuts,
        R.string.add_shortcuts_desc,
        R.string.recentlyListOnly,
        R.string.recentlyListOnly_desc,
        R.string.adult_only_content,
        R.string.adult_only_content_desc,
        R.string.descending_items,
        R.string.descending_items_desc,
        R.string.social_in_media,
        R.string.sync_history,
        R.string.comments_api,
        R.string.comments_api_desc,
        R.string.search_source_list,
        R.string.search_source_list_desc,
        R.string.view_subscriptions,
        R.string.view_subscriptions_desc,
        R.string.subscribe_lists,
        R.string.subscribe_lists_desc,
        R.string.clear_subscriptions,
        R.string.download_manager_select,
        R.string.download_manager_select_desc,
        R.string.delete_imported,
        R.string.delete_imported_desc,
        R.string.change_download_location,
        R.string.reset_download_location
    )

    val animeResources: ArrayList<Int> = arrayListOf(
        R.string.default_ep_view,
        R.string.player_settings,
        R.string.player_settings_desc,
        R.string.purge_anime_downloads,
        R.string.prefer_dub,
        R.string.prefer_dub_desc,
        R.string.show_yt,
        R.string.show_yt_desc,
        R.string.include_list,
        R.string.include_list_anime_desc,
        R.string.local_timezone,
        R.string.settings_torrent,
        R.string.torrent_port,
        R.string.port_range,
        R.string.episode_timeout,
        R.string.episode_timeout_desc
    )

    val mangaResources: ArrayList<Int> = arrayListOf(
        R.string.default_chp_view,
        R.string.layout,
        R.string.direction,
        R.string.dual_page,
        R.string.dual_page_info,
        R.string.source_info,
        R.string.auto_detect_webtoon,
        R.string.auto_detect_webtoon_info,
        R.string.over_scroll,
        R.string.image_long_clicking,
        R.string.true_colors,
        R.string.true_colors_info,
        R.string.hard_colors,
        R.string.hard_colors_info,
        R.string.photo_negative,
        R.string.auto_negative,
        R.string.image_rotation,
        R.string.crop_borders,
        R.string.spaced_pages,
        R.string.page_turning,
        R.string.hide_scroll_bar,
        R.string.hide_page_numbers,
        R.string.horizontal_scroll_bar,
        R.string.keep_screen_on,
        R.string.volume_buttons,
        R.string.wrap_images,
        R.string.wrap_images_info,
        R.string.line_height,
        R.string.margin,
        R.string.maximum_inline_size,
        R.string.maximum_column_width,
        R.string.maximum_block_size,
        R.string.maximum_height,
        R.string.use_dark_theme,
        R.string.use_oled_theme,
        R.string.keep_screen_on,
        R.string.volume_buttons,
        R.string.include_list,
        R.string.include_list_desc,
        R.string.show_system_bars,
        R.string.comic_format,
        R.string.comic_format_desc,
        R.string.purge_manga_downloads,
        R.string.purge_novel_downloads,
        R.string.ask_update_progress_manga,
        R.string.ask_update_progress_info_chap,
        R.string.ask_update_progress_chapter_zero,
        R.string.ask_update_progress_info_zero,
        R.string.ask_update_progress_doujin
    )

    val extensionResources: ArrayList<Int> = arrayListOf(
        R.string.anime_add_repository,
        R.string.add_repository_desc,
        R.string.manga_add_repository,
        R.string.add_repository_desc,
        R.string.novel_add_repository,
        R.string.add_repository_desc,
        R.string.extension_test,
        R.string.extension_test_desc,
        R.string.force_legacy_installer,
        R.string.force_legacy_installer_desc,
        R.string.skip_loading_extension_icons,
        R.string.skip_loading_extension_icons_desc,
        R.string.NSFWExtention,
        R.string.NSFWExtention_desc
    )

    val notificationResources: ArrayList<Int> = arrayListOf(
        R.string.notification_page,
        R.string.notification_page_desc,
        R.string.anilist_notification_filters,
        R.string.anilist_notification_filters_desc,
        R.string.notification_for_checking_subscriptions,
        R.string.notification_for_checking_subscriptions_desc,
        R.string.subscriptions_checking_time_s,
        R.string.subscriptions_info,
        R.string.anilist_notifications_checking_time,
        R.string.anilist_notifications_checking_time_desc,
        R.string.comment_notification_checking_time,
        R.string.comment_notification_checking_time_desc,
        R.string.use_alarm_manager_reliable,
        R.string.use_alarm_manager_reliable_desc
    )

    val addonResources: ArrayList<Int> = arrayListOf(
        R.string.anime_downloader_addon,
        R.string.not_installed
    )

    val systemResources: ArrayList<Int> = arrayListOf(
        R.string.selected_dns,
        R.string.user_agent,
        R.string.user_agent_desc,
        R.string.biometric_title,
        R.string.biometric_summary,
        R.string.client_mode,
        R.string.client_mode_desc,
        R.string.offline_ani,
        R.string.offline_dbs,
        R.string.update_offline_db,
        R.string.remove_offline_db,
        R.string.offline_ext,
        R.string.offline_res,
        R.string.backup_restore,
        R.string.backup_restore_desc,
        R.string.check_app_updates,
        R.string.check_app_updates_desc,
        R.string.share_username_in_logs,
        R.string.share_username_in_logs_desc,
        R.string.log_to_file,
        R.string.logging_warning,
        R.string.share_log_file,
        R.string.crashlytics,
        R.string.disable_debug,
        R.string.rogue_warning,
        R.string.clear_glide,
        R.string.clear_glide_desc
    )

    val aboutResources: ArrayList<Int> = arrayListOf(
        R.string.account_help,
        R.string.faq,
        R.string.faq_desc,
        R.string.devs,
        R.string.devs_desc,
        R.string.forks,
        R.string.forks_desc,
        R.string.disclaimer,
        R.string.disclaimer_desc,
        R.string.license,
        R.string.license_desc
    )
}

fun ArrayList<Int>.containsQuery(query: String?) : Boolean {
    if (query == null) return true
    return find {
        getString(it).lowercase(Locale.getDefault()).contains(query)
    } != null
}