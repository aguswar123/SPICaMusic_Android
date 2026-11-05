package me.spica27.spicamusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.spica27.spicamusic.R
import me.spica27.spicamusic.artwork.MusicArtwork
import me.spica27.spicamusic.ui.navigation.LocalBackStack
import me.spica27.spicamusic.ui.navigation.LyricsSourceRoute
import me.spica27.spicamusic.ui.theme.Shapes
import me.spica27.spicamusic.ui.theme.Spacing
import me.spica27.spicamusic.ui.widget.AudioCover
import me.spica27.spicamusic.ui.widget.LyricsCircleButton
import me.spica27.spicamusic.ui.widget.LyricsSettingsSheet
import me.spica27.spicamusic.ui.widget.LyricsToolbarMenuContent
import me.spica27.spicamusic.ui.widget.rememberLyricsEdgePadding
import org.koin.compose.viewmodel.koinActivityViewModel

private val LyricsToolbarBottomInset = 112.dp
private val LyricsHeaderCoverSize = 56.dp
private val LyricsHeaderButtonSize = 44.dp
private val LikedColor = Color(0xffcf1322)

/**
 * 展开播放器内部的全屏歌词前景。
 *
 * 该组件只负责展示与交互，不拥有导航状态；关闭歌词由播放器宿主的返回手势处理。
 */
@Composable
internal fun FullscreenLyricsSurface(
    title: String,
    artist: String,
    artwork: MusicArtwork?,
    isLike: Boolean,
    onLikeClick: () -> Unit,
    isPlaying: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        LyricsPanel(
            modifier = Modifier.fillMaxSize(),
            toolbarBottomInset = LyricsToolbarBottomInset,
            // 工具栏已移到顶栏右侧（三点按钮）
            showToolbar = false,
        )

        FullscreenLyricsHeader(
            title = title,
            artist = artist,
            artwork = artwork,
            isLike = isLike,
            onLikeClick = onLikeClick,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

@Composable
private fun FullscreenLyricsHeader(
    title: String,
    artist: String,
    artwork: MusicArtwork?,
    isLike: Boolean,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 与歌词文字的左边距一致，左右对称
    val edge = rememberLyricsEdgePadding()

    // 歌词工具栏（偏移 / 歌词设置 / 切换歌词）：原来在右下角，现在从三点按钮向下展开
    val viewModel: LyricsViewModel = koinActivityViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val styleViewModel: LyricsStyleViewModel = koinActivityViewModel()
    val styleSettings by styleViewModel.style.collectAsStateWithLifecycle()
    val backStack = LocalBackStack.current
    var menuExpanded by remember { mutableStateOf(false) }
    var showLyricsSettings by remember { mutableStateOf(false) }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = edge, vertical = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(LyricsHeaderCoverSize)
                        .clip(Shapes.MediumCornerBasedShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                AudioCover(
                    artwork = artwork,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // 喜欢按钮：与右侧三点按钮同款圆形背景
                LyricsCircleButton(
                    onClick = onLikeClick,
                    size = LyricsHeaderButtonSize,
                ) {
                    Icon(
                        imageVector = if (isLike) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.favorite),
                        tint = if (isLike) LikedColor else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
                LyricsCircleButton(
                    onClick = { menuExpanded = !menuExpanded },
                    size = LyricsHeaderButtonSize,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription =
                            if (menuExpanded) {
                                stringResource(R.string.collapse)
                            } else {
                                stringResource(R.string.lyrics_tool)
                            },
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }

        // 从三点按钮下方展开，右边距与顶栏一致
        AnimatedVisibility(
            visible = menuExpanded,
            modifier = Modifier.align(Alignment.End).padding(end = edge),
            enter =
                fadeIn(spring(stiffness = Spring.StiffnessMedium)) +
                    expandVertically(spring(stiffness = Spring.StiffnessMedium)),
            exit =
                fadeOut(spring(stiffness = Spring.StiffnessMedium)) +
                    shrinkVertically(spring(stiffness = Spring.StiffnessMedium)),
        ) {
            LyricsToolbarMenuContent(
                offsetEnabled = !uiState.lyricsSuppressed,
                offsetMs = uiState.lyricsOffsetMs,
                onOffsetChange = { viewModel.updateOffset(it) },
                onOpenLyricsSettings = {
                    menuExpanded = false
                    showLyricsSettings = true
                },
                onOpenLyricsSwitcher = {
                    menuExpanded = false
                    viewModel.openPanel()
                    backStack.add(LyricsSourceRoute)
                },
            )
        }
    }

    if (showLyricsSettings) {
        LyricsSettingsSheet(
            settings = styleSettings,
            onUpdate = styleViewModel::update,
            onDismiss = { showLyricsSettings = false },
        )
    }
}
