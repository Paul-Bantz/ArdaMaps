/*
 * This file is part of ArdaMaps, licensed under the MIT License (MIT).
 *
 * Copyright (c) Paul-Bantz <https://github.com/Paul-Bantz>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.duom.ardamaps.gui.screens;

import com.duom.ardamaps.ArdaMapsClient;
import com.duom.ardamaps.core.data.conversion.ContentBlock;
import com.duom.ardamaps.core.data.conversion.HtmlConverter;
import com.duom.ardamaps.core.data.guide.*;
import com.duom.ardamaps.gui.ModConstants;
import com.duom.ardamaps.gui.screens.rendering.BackgroundRenderer;
import com.duom.ardamaps.gui.screens.rendering.TextContentBlockRenderer;
import com.duom.ardamaps.gui.widgets.ScrollbarWidget;
import com.duom.ardamaps.gui.widgets.StyledButtonWidget;
import com.duom.ardamaps.gui.widgets.builders.StyledButtonBuilder;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Displays the ArdaMaps in-game guide loaded from {@code assets/ardamaps/guide/guide.json}.
 *
 * <p>The screen is split into three visible areas:</p>
 * <ul>
 *   <li><b>Left column</b> – scrollable list of page-category toggle buttons.</li>
 *   <li><b>Entry sub-column</b> (≈30 % of the right half) – scrollable list of entry
 *       buttons for the active page.</li>
 *   <li><b>Content sub-column</b> (≈60 % of the right half) – scrollable HTML content
 *       rendered for the selected entry.</li>
 * </ul>
 *
 * <p>The entry sub-column and content sub-column are separated by a 1 px vertical rule.
 * Switching to a page immediately loads and highlights the first entry. Clicking any
 * entry button loads its content into the content sub-column and highlights the
 * corresponding button.</p>
 *
 * <p>All async operations (book loading, HTML fetching) are scheduled back onto the
 * Minecraft render thread via {@code MinecraftClient#execute} before mutating UI state.</p>
 */
public class GuideScreen extends ArdaMapsScreen {

    /** Width in pixels of every scrollbar rendered in this screen. */
    private static final int SCROLLBAR_WIDTH = 4;

    /** Horizontal gap in pixels between a scrollbar and the text it accompanies. */
    private static final int SCROLLBAR_MARGIN = 3;

    /** Pixel distance scrolled per mouse-wheel notch in the HTML content sub-column. */
    private static final int SCROLL_SPEED = 12;

    /**
     * Number of list items scrolled per mouse-wheel notch in button lists.
     * A value of 1 means one button scrolls per notch.
     */
    private static final int BUTTON_SCROLL_SPEED = 1;

    /**
     * Screen layout view state – controls what the left column renders.
     * {@code PAGE_LIST} shows the page-selection buttons; {@code ENTRY_VIEW}
     * shows the entries of the selected page plus a back button.
     */
    private enum ViewState { PAGE_LIST, ENTRY_VIEW }

    /**
     * Inner margin values applied to the left page column.
     * These control the space between the column's logical boundary and its content.
     */
    private static final Margins LEFT_MARGINS = new Margins(10, 32, 10, 10);

    /**
     * Inner margin values applied to the right area (both sub-columns share these
     * outer margins; only the top, bottom, left, and right edges of the combined
     * right area are affected).
     */
    private static final Margins RIGHT_MARGINS = new Margins(32, 10, 10, 10);

    /** Translated label used as the section title of the left column. */
    private final Text titleGuide = Text.translatable("ardamaps.client.map.screen.guide");

    /** Translated label used as the title of the right column when no page is selected (landing page). */
    private final Text titleAbout = Text.translatable("ardamaps.client.map.screen.guide.about");

    /** Translated placeholder shown while the guide book or an entry is loading. */
    private final Text textLoading = Text.translatable("ardamaps.client.map.screen.guide.loading");

    /** Translated placeholder shown in the entry sub-column when a page has no entries. */
    private final Text textSelectTopic = Text.translatable("ardamaps.client.map.screen.guide.select_topic");

    /**
     * One toggle button per {@link com.duom.ardamaps.core.data.guide.GuidePage},
     * built in {@link #rebuildPageButtons()} and rendered in the left column.
     * The button whose index matches {@link #currentPageIndex} is kept in the
     * toggled (highlighted) state.
     */
    private final List<StyledButtonWidget> pageButtons = new ArrayList<>();

    /**
     * One button per {@link GuideEntry} of the active page, built in
     * {@link #rebuildEntryButtons(int)} and rendered in the entry sub-column.
     * The button whose index matches {@link #selectedEntryIndex} is kept in the
     * toggled (highlighted) state.
     */
    private final List<StyledButtonWidget> entryButtons = new ArrayList<>();

    /**
     * Item-based scrollbar controlling which page buttons are visible in the left
     * column. One scroll notch advances the list by {@link #BUTTON_SCROLL_SPEED} item.
     */
    private final ScrollbarWidget leftScrollbar =
            new ScrollbarWidget(SCROLLBAR_WIDTH, 12, ModConstants.COLOR_BLUE, ModConstants.COLOR_BLUE_HIGHLIGHT, BUTTON_SCROLL_SPEED);

    /**
     * Item-based scrollbar controlling which entry buttons are visible in the entry
     * sub-column. One scroll notch advances the list by {@link #BUTTON_SCROLL_SPEED} item.
     */
    private final ScrollbarWidget entryScrollbar =
            new ScrollbarWidget(SCROLLBAR_WIDTH, 12, ModConstants.COLOR_BLUE, ModConstants.COLOR_BLUE_HIGHLIGHT, BUTTON_SCROLL_SPEED);

    /**
     * Pixel-based scrollbar controlling the vertical scroll position of the rendered
     * HTML content in the content sub-column. One scroll notch moves the content by
     * {@link #SCROLL_SPEED} pixels.
     */
    private final ScrollbarWidget contentScrollbar =
            new ScrollbarWidget(SCROLLBAR_WIDTH, 12, ModConstants.COLOR_BLUE, ModConstants.COLOR_BLUE_HIGHLIGHT, SCROLL_SPEED);

    /**
     * The fully loaded guide book, or {@code null} while the initial async load is in
     * progress. Marked {@code volatile} because it is written on a background thread
     * and read on the render thread.
     */
    private volatile GuideBook guideBook;

    /**
     * {@code true} from the moment the async guide-book fetch starts until the result
     * is delivered to the render thread. Marked {@code volatile} for cross-thread
     * visibility.
     */
    private volatile boolean loadingBook = true;

    /**
     * {@code true} from the moment an async HTML fetch starts until its result is
     * delivered to the render thread. Marked {@code volatile} for cross-thread
     * visibility.
     */
    private volatile boolean loadingEntry = false;

    /**
     * {@code true} from the moment the async {@code guide.html} load starts until its
     * result is delivered to the render thread.
     */
    private volatile boolean loadingLanding = false;

    /**
     * Zero-based index of the currently active page into {@link GuideBook#getPages()},
     * or {@code -1} before the first page has been selected (i.e., while the book is
     * still loading).
     */
    private int currentPageIndex = -1;

    /**
     * Zero-based index of the currently selected entry within the active page's entry
     * list, or {@code -1} when no entry is selected (e.g., on pages that have no
     * entries or immediately after switching pages before the first auto-select fires).
     */
    private int selectedEntryIndex = -1;

    /**
     * Screen-space Y coordinate of the top edge of the scrollable page-button list
     * in the left column. Updated every frame in {@link #renderLeftColumn}.
     * Used in {@link #mouseClicked} and {@link #mouseScrolled} to reject events on
     * buttons that have scrolled out of view.
     */
    private int leftListTopY;

    /**
     * Screen-space Y coordinate of the bottom edge of the left column's viewport.
     * Updated every frame in {@link #renderLeftColumn}.
     */
    private int leftBottomY;

    /**
     * Screen-space Y coordinate of the top edge of both right sub-columns' scrollable
     * viewport. Updated every frame in {@link #renderRightColumn}.
     * Used in {@link #mouseClicked} and {@link #mouseScrolled} to guard against events
     * above the scrollable area (e.g., the title / separator row).
     */
    private int rightSubTopY;

    /**
     * Screen-space Y coordinate of the bottom edge of both right sub-columns.
     * Updated every frame in {@link #renderRightColumn}.
     */
    private int rightSubBottomY;

    /**
     * Current view state – determines what the left column renders each frame.
     * Starts as {@link ViewState#PAGE_LIST} and switches to
     * {@link ViewState#ENTRY_VIEW} when the user selects a page.
     */
    private ViewState viewState = ViewState.PAGE_LIST;

    /**
     * Back button rendered at the bottom of the left column while in
     * {@link ViewState#ENTRY_VIEW}. Clicking it returns the user to the page list.
     * Built in {@link #rebuildPageButtons()}.
     */
    private StyledButtonWidget backButton;

    /**
     * The parsed content blocks of the currently selected entry, converted from HTML by
     * {@link HtmlConverter#parseBlocks(String)}. {@code null} while loading or when no
     * entry is selected.
     */
    private List<ContentBlock> currentContent = null;

    /**
     * Content blocks parsed from {@code guide.html}, displayed on the right page while
     * in {@link ViewState#PAGE_LIST}. {@code null} if the file does not exist or has not
     * yet finished loading.
     */
    private List<ContentBlock> landingContent = null;

    /**
     * Renderer for the HTML content blocks
     */
    private TextContentBlockRenderer textContentBlockRenderer;

    /**
     * The {@link Style} under the mouse cursor in the content sub-column as of the last
     * rendered frame. Updated every frame by {@link #renderContentSubColumn} and read by
     * {@link #mouseClicked} to implement Shift+click -> chat and click -> controls screen.
     */
    @Nullable
    private Style lastHoveredContentStyle;

    /**
     * Deep-link string from {@code ClientConfig#lastPage} supplied at construction time.
     * Used in {@link #init()} to restore the last-visited page+entry once the book has
     * finished loading. {@code null} or {@code "guide"} means open the landing page.
     */
    @Nullable
    private final String initialLink;

    /**
     * Creates a new Guide Screen that attempts to restore the given deep-link.
     *
     * @param parent      the screen to return to when this screen is closed
     * @param initialLink a deep-link such as {@code "guide"} or {@code "guide:page:start_here/about"},
     *                    or {@code null} to open the landing page
     */
    public GuideScreen(Screen parent, @Nullable String initialLink) {
        super(parent, Text.translatable("ardamaps.client.map.screen.guide"));
        this.initialLink = initialLink;
    }

    /**
     * Initializes the screen and (re-)triggers an asynchronous load of the guide book.
     *
     * <p>This method is called both when the screen is first opened and whenever
     * Minecraft resizes the window (which calls {@code init()} again). Re-triggering
     * the load on every {@code init()} ensures the guide reflects the latest
     * resource-pack state after a reload.</p>
     *
     * <p>Once the book arrives on the background thread it is stored in
     * {@link #guideBook} and {@link #rebuildPageButtons()} is scheduled on the
     * render thread.</p>
     */
    @Override
    protected void init() {
        super.init();

        textContentBlockRenderer = new TextContentBlockRenderer(textRenderer, ModConstants.COLOR_DARK_BROWN);
        loadingBook = true;
        loadingLanding = true;
        landingContent = null;

        // Load guide.html in parallel with the book – shown on right page in PAGE_LIST state
        GuideLoader.loadHtml("guide.html").thenAccept(html -> {
            List<ContentBlock> parsed = html.isBlank() ? null : HtmlConverter.parseBlocks(html);
            assert client != null;
            client.execute(() -> {
                landingContent = parsed;
                loadingLanding = false;
            });
        });

        GuideLoader.loadGuideBook().thenAccept(book -> {
            this.guideBook = book;
            this.loadingBook = false;

            GuideSearchIndex.preloadIfNeeded(book);

            assert client != null;
            client.execute(() -> {

                rebuildPageButtons();

                // Restore the last-visited page+entry from the deep-link (first init only)
                if (currentPageIndex < 0) {

                    GuideScreenLink.Resolved resolved = GuideScreenLink.resolve(initialLink, guideBook);

                    if (resolved != null) {

                        selectPage(resolved.pageIndex());

                        // selectPage auto-selects entry 0; override with the stored entry if different
                        if (resolved.entryIndex() != 0) {

                            var entries = guideBook.getPages().get(resolved.pageIndex()).getEntries();

                            if (resolved.entryIndex() < entries.size()) {
                                selectEntry(entries.get(resolved.entryIndex()), resolved.entryIndex());
                            }
                        }
                    }
                }
            });
        });
    }

    /**
     * Get the search function that is called when searching an element on screen via the search widget.
     * This function should search for a String in a List of elements represented on screen
     *
     * @return  the search function
     */
    @Override
    protected @Nullable Function<String, List<?>> getSearchFunction() {
        return input -> GuideSearchIndex.search(guideBook, input);
    }

    /**
     * Gets the rendering function of a search result. This function takes an element as an input and returns a
     * displayable string
     *
     * @return the search result rendering function
     */
    @Override
    protected @Nullable Function<Object, String> getSearchResultRenderFunction() {
        return GuideSearchIndex::renderResult;
    }

    @Override
    protected @Nullable Function<Object, String> getSearchResultTooltipFunction() {
        return GuideSearchIndex::renderTooltip;
    }

    /**
     * Gets the function that is called when a search result is selected via the search widget.
     *
     * @return the function called when a search result is selected
     */
    @Override
    protected Function<Object, Void> getOnSearcheResultSelectedFunction() {
        return (element) -> {

            if (!(element instanceof GuideSearchIndex.GuideSearchResult result) || guideBook == null) return null;

            if (result.pageIndex() < 0 || result.pageIndex() >= guideBook.getPages().size()) return null;

            selectPage(result.pageIndex());

            var entries = guideBook.getPages().get(result.pageIndex()).getEntries();
            if (result.entryIndex() >= 0 && result.entryIndex() < entries.size()) {
                selectEntry(entries.get(result.entryIndex()), result.entryIndex());
            }

            return null;
        };
    }

    /**
     * Switches the active page to {@code pageIndex}.
     *
     * <p>This method:</p>
     * <ol>
     *   <li>Updates {@link #currentPageIndex} and resets both scrollbars and
     *       {@link #selectedEntryIndex}.</li>
     *   <li>Rebuilds the entry button list for the new page.</li>
     *   <li>Updates the page-button toggle highlights.</li>
     *   <li>Automatically selects the first entry (if the page has any) so the
     *       content sub-column is never blank after a page change.</li>
     * </ol>
     *
     * @param pageIndex zero-based index into {@link GuideBook#getPages()}
     */
    private void selectPage(int pageIndex) {
        viewState = ViewState.ENTRY_VIEW;
        currentPageIndex = pageIndex;
        selectedEntryIndex = -1;
        currentContent = null;
        contentScrollbar.resetOffset();
        entryScrollbar.resetOffset();

        rebuildEntryButtons(pageIndex);
        syncPageToggleStates(pageIndex);

        // Auto-select the first entry so the content sub-column is never blank after a page change
        if (guideBook != null && pageIndex >= 0 && pageIndex < guideBook.getPages().size()) {
            var entries = guideBook.getPages().get(pageIndex).getEntries();
            if (!entries.isEmpty()) {
                selectEntry(entries.get(0), 0);
            }
        }
    }

    /**
     * Loads the HTML content for {@code entry} and highlights its button.
     *
     * <p>The highlight is applied immediately (before the async fetch completes) so
     * the user receives instant visual feedback. The content sub-column shows the
     * {@link #textLoading} placeholder until the fetch finishes.</p>
     *
     * <p>The HTML string is parsed into content blocks by
     * {@link HtmlConverter#parseBlocks(String)} on the background thread; the result is
     * then delivered to the render thread via {@code MinecraftClient#execute}.</p>
     *
     * @param entry      the {@link GuideEntry} whose HTML file to fetch
     * @param entryIndex zero-based position of {@code entry} within the active page's
     *                   entry list; used to identify which button to toggle
     */
    private void selectEntry(GuideEntry entry, int entryIndex) {
        selectedEntryIndex = entryIndex;
        syncEntryToggleStates();

        loadingEntry = true;
        currentContent = null;
        contentScrollbar.resetOffset();

        // Persist the deep-link for this page+entry
        if (guideBook != null && currentPageIndex >= 0 && currentPageIndex < guideBook.getPages().size()) {

            var page = guideBook.getPages().get(currentPageIndex);
            ArdaMapsClient.CONFIG.setLastPage(GuideScreenLink.encodePage(page, entry));
        }

        GuideLoader.loadHtml(entry.getLink()).thenAccept(html -> {
            List<ContentBlock> parsed = html.isBlank()
                    ? List.of(new ContentBlock.TextBlock(Text.literal("(no content)")))
                    : HtmlConverter.parseBlocks(html);

            assert client != null;
            client.execute(() -> {
                currentContent = parsed;
                loadingEntry = false;
            });
        });
    }

    /**
     * Builds (or rebuilds) one toggle button per page in {@link GuideBook#getPages()}
     * and adds them to {@link #pageButtons}.
     *
     * <p>Called from the render thread after the async guide-book load completes, and
     * again after every window resize (via {@link #init()}).</p>
     *
     * <ul>
     *   <li>If {@link #currentPageIndex} is {@code -1} (first load), page 0 is
     *       selected automatically.</li>
     *   <li>Otherwise the existing selection is restored without reloading the HTML
     *       content, so a window resize does not interrupt reading.</li>
     * </ul>
     */
    private void rebuildPageButtons() {
        pageButtons.clear();
        leftScrollbar.resetOffset();

        backButton = StyledButtonBuilder.create()
                .setText(Text.translatable("ardamaps.client.map.screen.guide.back"))
                .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                .setOnClick(() -> {
                    viewState = ViewState.PAGE_LIST;
                    currentPageIndex = -1;
                    selectedEntryIndex = -1;
                    currentContent = null;
                    leftScrollbar.resetOffset();
                    entryScrollbar.resetOffset();
                    contentScrollbar.resetOffset();

                    ArdaMapsClient.CONFIG.setLastPage(GuideScreenLink.GUIDE);

                    rebuildPageButtons();
                })
                .build();

        if (guideBook == null) return;

        var pages = guideBook.getPages();
        for (int i = 0; i < pages.size(); i++) {
            final int idx = i;
            var page = pages.get(i);
            var button = StyledButtonBuilder.create()
                    .setText(Text.literal(page.getTitle()))
                    .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                    .setOnClick(() -> selectPage(idx))
                    .build();
            pageButtons.add(button);
        }

        if (!pages.isEmpty()) {
            if (currentPageIndex >= 0) {
                // Restore selection after resize – don't change viewState or reload content
                syncPageToggleStates(currentPageIndex);
                rebuildEntryButtons(currentPageIndex);
                syncEntryToggleStates();
            }
            // currentPageIndex < 0 -> stay in PAGE_LIST, landing content shows on the right
        }
    }

    /**
     * Builds one button per {@link GuideEntry} on the given page and populates
     * {@link #entryButtons}.
     *
     * <p>Each button's {@code onClick} handler calls
     * {@link #selectEntry(GuideEntry, int)} with both the entry and its index so
     * the correct button can be highlighted.</p>
     *
     * @param pageIndex zero-based index into {@link GuideBook#getPages()}; out-of-range
     *                  values are silently ignored
     */
    private void rebuildEntryButtons(int pageIndex) {
        entryButtons.clear();

        if (guideBook == null) return;
        var pages = guideBook.getPages();
        if (pageIndex < 0 || pageIndex >= pages.size()) return;

        var entries = pages.get(pageIndex).getEntries();

        for (int i = 0; i < entries.size(); i++) {

            final int ei = i;
            final var entry = entries.get(i);

            var btn = StyledButtonBuilder.create()
                    .setText(Text.literal(entry.getTitle()))
                    .setSize(ModConstants.BUTTON_WIDTH, ModConstants.BUTTON_HEIGHT)
                    .setOnClick(() -> selectEntry(entry, ei))
                    .setStyle(StyledButtonWidget.Style.EDGE)
                    .build();
            entryButtons.add(btn);
        }
    }

    /**
     * Iterates {@link #pageButtons} and calls {@link StyledButtonWidget#setToggled}
     * so that only the button at {@code activeIndex} appears highlighted.
     *
     * @param activeIndex zero-based index of the page button to highlight
     */
    private void syncPageToggleStates(int activeIndex) {
        for (int i = 0; i < pageButtons.size(); i++) {
            pageButtons.get(i).setToggled(i == activeIndex);
        }
    }

    /**
     * Iterates {@link #entryButtons} and calls {@link StyledButtonWidget#setToggled}
     * so that only the button at {@link #selectedEntryIndex} appears highlighted.
     * All other entry buttons are un-toggled.
     */
    private void syncEntryToggleStates() {
        for (int i = 0; i < entryButtons.size(); i++) {
            entryButtons.get(i).setToggled(i == selectedEntryIndex);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Renders the tiled paper background, then the guide UI (three columns), then
     * the standard Minecraft overlay elements (tooltips, etc.).</p>
     */
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        renderGuideUi(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    /**
     * Computes the shared layout geometry (column origins, total widths, bottom edge)
     * from the padded content area and delegates to {@link #renderLeftColumn} and
     * {@link #renderRightColumn}.
     *
     * @param context the draw context for the current frame
     * @param mouseX  current mouse X in screen coordinates
     * @param mouseY  current mouse Y in screen coordinates
     * @param delta   partial tick used for animations
     */
    private void renderGuideUi(DrawContext context, int mouseX, int mouseY, float delta) {
        var contentArea = getPaddedContentArea();

        int leftColX = contentArea.topLeftX() + LEFT_MARGINS.left();
        int rightColX = contentArea.topLeftX() + (contentArea.guiWidth() / 2) + RIGHT_MARGINS.left();
        int pageWidth = computePageWidth(contentArea);
        int contentBottomY = contentArea.topLeftY() + contentArea.guiHeight() - RIGHT_MARGINS.bottom();

        renderLeftColumn(context,
                leftColX,
                contentArea.topLeftY() + LEFT_MARGINS.top(),
                contentBottomY - LEFT_MARGINS.bottom(),
                pageWidth, mouseX, mouseY, delta);

        renderRightColumn(context,
                rightColX,
                contentArea.topLeftY() + RIGHT_MARGINS.top(),
                contentBottomY - RIGHT_MARGINS.bottom(),
                pageWidth, mouseX, mouseY, delta);
    }

    /**
     * Calculates the page width available for rendering
     * @param contentArea the content area provided by the screen
     * @return the available page width
     */
    private int computePageWidth(BackgroundRenderer.GuiLayout contentArea) {

        return (contentArea.guiWidth() / 2) - (LEFT_MARGINS.left() + LEFT_MARGINS.right());
    }

    /**
     * Renders the left column. Delegates to {@link #renderPageListLeftColumn} or
     * {@link #renderEntryViewLeftColumn} depending on {@link #viewState}.
     */
    private void renderLeftColumn(DrawContext context, int colX, int topY, int bottomY,
                                  int pageWidth, int mouseX, int mouseY, float delta) {
        if (viewState == ViewState.ENTRY_VIEW) {
            renderEntryViewLeftColumn(context, colX, topY, bottomY, pageWidth, mouseX, mouseY, delta);
        } else {
            renderPageListLeftColumn(context, colX, topY, bottomY, pageWidth, mouseX, mouseY, delta);
        }
    }

    /**
     * Left-column rendering for {@link ViewState#PAGE_LIST}: guide title, separator,
     * and a scrollable list of page toggle buttons.
     */
    private void renderPageListLeftColumn(DrawContext context, int colX, int topY, int bottomY,
                                          int pageWidth, int mouseX, int mouseY, float delta) {
        int y = topY;
        y = renderSectionTitle(context, colX, y, pageWidth, titleGuide) + ModConstants.ROW_SPACING;
        y += ScreenRenderingUtils.renderSeparator(context, pageWidth, colX, y) + ModConstants.ROW_SPACING;

        leftListTopY = y;
        leftBottomY = bottomY;

        if (loadingBook) {
            context.drawText(textRenderer, textLoading, colX, y, ModConstants.COLOR_DARK_BROWN, false);
            return;
        }

        int visibleHeight = bottomY - y;
        if (visibleHeight <= 0 || pageButtons.isEmpty()) return;

        int stride = ModConstants.BUTTON_HEIGHT + ModConstants.ROW_SPACING;
        int visibleItems = Math.max(1, (visibleHeight + ModConstants.ROW_SPACING) / stride);
        int maxScroll = Math.max(0, pageButtons.size() - visibleItems);
        leftScrollbar.setMaxOffset(maxScroll);

        context.enableScissor(colX, y, colX + pageWidth, bottomY);

        int drawY = y - leftScrollbar.getScrollOffset() * stride;
        for (StyledButtonWidget btn : pageButtons) {
            btn.setX(colX + pageWidth / 2 - btn.getWidth() / 2);
            btn.setY(drawY);
            if (drawY + ModConstants.BUTTON_HEIGHT > y && drawY < bottomY) {
                btn.render(context, mouseX, mouseY, delta);
            }
            drawY += stride;
        }

        context.disableScissor();

        if (maxScroll > 0) {
            leftScrollbar.render(context,
                    colX + pageWidth - SCROLLBAR_WIDTH, y,
                    stride * visibleItems, visibleItems, pageButtons.size());
        }
    }

    /**
     * Left-column rendering for {@link ViewState#ENTRY_VIEW}: the selected page's name,
     * a separator, a scrollable list of entry buttons, and a back button pinned at the
     * very bottom of the column (always visible, outside the scissor region).
     */
    private void renderEntryViewLeftColumn(DrawContext context, int colX, int topY, int bottomY,
                                           int pageWidth, int mouseX, int mouseY, float delta) {
        int y = topY;

        String pageTitle = (guideBook != null && currentPageIndex >= 0
                && currentPageIndex < guideBook.getPages().size())
                ? guideBook.getPages().get(currentPageIndex).getTitle()
                : "";
        y = renderSectionTitle(context, colX, y, pageWidth, Text.literal(pageTitle)) + ModConstants.ROW_SPACING;
        y += ScreenRenderingUtils.renderSeparator(context, pageWidth, colX, y) + ModConstants.ROW_SPACING;

        // Reserve space for the back button at the bottom
        int backButtonStride = ModConstants.BUTTON_HEIGHT + ModConstants.ROW_SPACING;
        int listBottomY = bottomY - backButtonStride;

        leftListTopY = y;
        leftBottomY = listBottomY;

        if (entryButtons.isEmpty()) {
            context.drawText(textRenderer, textSelectTopic, colX, y, ModConstants.COLOR_DARK_BROWN, false);
        } else {
            int visibleHeight = listBottomY - y;
            if (visibleHeight > 0) {
                int stride = ModConstants.BUTTON_HEIGHT;
                int visibleItems = Math.max(1, visibleHeight / stride);
                int maxScroll = Math.max(0, entryButtons.size() - visibleItems);
                entryScrollbar.setMaxOffset(maxScroll);

                context.enableScissor(colX, y, colX + pageWidth, listBottomY);

                int drawY = y - entryScrollbar.getScrollOffset() * stride;
                for (StyledButtonWidget btn : entryButtons) {
                    btn.setWidth(pageWidth);
                    btn.setHeight(ModConstants.BUTTON_HEIGHT);
                    btn.setX(colX);
                    btn.setY(drawY);
                    if (drawY + ModConstants.BUTTON_HEIGHT > y && drawY < listBottomY) {
                        btn.render(context, mouseX, mouseY, delta);
                    }
                    drawY += stride;
                }

                context.disableScissor();

                if (maxScroll > 0) {
                    entryScrollbar.render(context,
                            colX + pageWidth - SCROLLBAR_WIDTH, y,
                            stride * visibleItems, visibleItems, entryButtons.size());
                }
            }
        }

        // Back button – always visible, pinned at the bottom of the column
        backButton.setWidth(ModConstants.BUTTON_WIDTH);
        backButton.setHeight(ModConstants.BUTTON_HEIGHT);
        backButton.setX(colX + pageWidth / 2 - backButton.getWidth() / 2);
        backButton.setY(bottomY - ModConstants.BUTTON_HEIGHT);
        backButton.render(context, mouseX, mouseY, delta);
    }

    /**
     * Renders the right page of the screen: a page title, a horizontal separator, and
     * the full-width scrollable HTML content for the selected entry.
     *
     * <p>A loading placeholder is shown while the book is loading or no page has been
     * selected yet ({@link #currentPageIndex} {@code < 0}).</p>
     *
     * <p>Updates {@link #rightSubTopY} and {@link #rightSubBottomY} each frame for
     * use in input routing.</p>
     *
     * @param context   the draw context for the current frame
     * @param colX      left edge X of the right area in screen coordinates
     * @param topY      top edge Y of the right area in screen coordinates
     * @param bottomY   bottom edge Y of the right area in screen coordinates
     * @param pageWidth total pixel width available for the right area
     * @param mouseX    current mouse X in screen coordinates
     * @param mouseY    current mouse Y in screen coordinates
     * @param delta     partial tick (unused; kept for signature consistency with the caller)
     */
    @SuppressWarnings("unused")
    private void renderRightColumn(DrawContext context, int colX, int topY, int bottomY,
                                   int pageWidth, int mouseX, int mouseY, float delta) {
        int y = topY;

        if (viewState == ViewState.PAGE_LIST) {
            // Show "About" title and the landing page content (guide.html)
            y = renderSectionTitle(context, colX, y, pageWidth, titleAbout) + ModConstants.ROW_SPACING;
            y += ScreenRenderingUtils.renderSeparator(context, pageWidth, colX, y) + ModConstants.ROW_SPACING;
            rightSubTopY = y;
            rightSubBottomY = bottomY;
            renderContentSubColumn(context, colX, y, pageWidth, bottomY, mouseX, mouseY,
                    loadingLanding, landingContent);
            return;
        }

        // ENTRY_VIEW
        if (loadingBook || currentPageIndex < 0) {
            context.drawText(textRenderer, textLoading, colX, topY, ModConstants.COLOR_DARK_BROWN, false);
            return;
        }

        String entryTitle = "";
        if (guideBook != null && currentPageIndex < guideBook.getPages().size()
                && selectedEntryIndex >= 0) {
            var entries = guideBook.getPages().get(currentPageIndex).getEntries();
            if (selectedEntryIndex < entries.size()) {
                entryTitle = entries.get(selectedEntryIndex).getTitle();
            }
        }

        y = renderSectionTitle(context, colX, y, pageWidth, Text.literal(entryTitle)) + ModConstants.ROW_SPACING;
        y += ScreenRenderingUtils.renderSeparator(context, pageWidth, colX, y) + ModConstants.ROW_SPACING;

        rightSubTopY = y;
        rightSubBottomY = bottomY;

        renderContentSubColumn(context, colX, y, pageWidth, bottomY, mouseX, mouseY,
                loadingEntry, currentContent);
    }

    /**
     * Draws {@code title} centred horizontally within {@code pageWidth}, scaled to
     * 1.4× the base font size.
     *
     *
     * @param context   the draw context for the current frame
     * @param x         left edge X of the column this title belongs to
     * @param y         top edge Y at which to begin drawing the title
     * @param pageWidth pixel width of the column (used for horizontal centring)
     * @param title     the text to render
     * @return the screen-space Y coordinate immediately below the rendered title,
     * suitable for use as the starting Y of the next element
     */
    private int renderSectionTitle(DrawContext context, int x, int y, int pageWidth, Text title) {
        float scale = 1.4f;
        int textW = textRenderer.getWidth(title);
        int xOffset = (int) (pageWidth / 2f - (textW * scale / 2f));

        context.getMatrices().push();
        context.getMatrices().translate(x + xOffset, y, 0);
        context.getMatrices().scale(scale, scale, 1f);
        context.drawText(textRenderer, title, 0, 0, ModConstants.COLOR_DARK_BROWN, false);
        context.getMatrices().pop();

        return (int) (y + textRenderer.fontHeight * scale);
    }


    /**
     * Renders the content sub-column with the given {@code content} blocks,
     * scrolled by {@link #contentScrollbar}.
     *
     * <p>If {@code loading} is {@code true} or {@code content} is {@code null},
     * a loading placeholder is displayed instead.</p>
     *
     * @param context  the draw context for the current frame
     * @param x        left edge X of this sub-column in screen coordinates
     * @param y        top edge Y of the scrollable area in screen coordinates
     * @param subWidth pixel width of this sub-column
     * @param bottomY  bottom edge Y of the scrollable area in screen coordinates
     * @param mouseX   current mouse X in screen coordinates
     * @param mouseY   current mouse Y in screen coordinates
     * @param loading  {@code true} while the content is still being fetched/parsed
     * @param content  the parsed content blocks to render, or {@code null}
     */
    private void renderContentSubColumn(DrawContext context, int x, int y,
                                        int subWidth, int bottomY,
                                        int mouseX, int mouseY,
                                        boolean loading, @Nullable List<ContentBlock> content) {
        if (loading || content == null) {
            lastHoveredContentStyle = null;
            context.drawText(textRenderer, textLoading, x, y, ModConstants.COLOR_DARK_BROWN, false);
            return;
        }

        int viewportHeight = bottomY - y;
        if (viewportHeight <= 0) return;

        int textWrapWidth = subWidth - SCROLLBAR_WIDTH - SCROLLBAR_MARGIN;

        context.enableScissor(x, y, x + subWidth, y + viewportHeight);

        TextContentBlockRenderer.RenderResult result = textContentBlockRenderer.render(
                context, content,
                x, y, textWrapWidth,
                y + viewportHeight,
                contentScrollbar.getScrollOffset(),
                mouseX, mouseY
        );

        int totalHeight = result.totalHeight;
        int maxScroll   = Math.max(0, totalHeight - viewportHeight);
        contentScrollbar.setMaxOffset(maxScroll);

        if (maxScroll > 0) {
            contentScrollbar.render(context,
                    x + subWidth - SCROLLBAR_WIDTH, y,
                    viewportHeight, viewportHeight, totalHeight);
        }

        context.disableScissor();

        lastHoveredContentStyle = result.hoveredStyle;
        if (lastHoveredContentStyle != null && lastHoveredContentStyle.getHoverEvent() != null) {
            context.drawHoverEvent(textRenderer, lastHoveredContentStyle, mouseX, mouseY);
        }
    }


    /**
     * Handles mouse button press events.
     *
     * <p>Click events are dispatched in the following priority order:</p>
     * <ol>
     *   <li>Content style links (Shift+click -> chat, click -> controls) when the cursor
     *       is inside the right-page viewport.</li>
     *   <li>Left column buttons: in {@link ViewState#ENTRY_VIEW} the entry buttons and
     *       the back button; in {@link ViewState#PAGE_LIST} the page buttons.</li>
     *   <li>The default Minecraft screen handler for any remaining widgets.</li>
     * </ol>
     *
     * @param mouseX screen-space X of the cursor
     * @param mouseY screen-space Y of the cursor
     * @param button the mouse button index (0 = left, 1 = right, 2 = middle)
     * @return {@code true} if the event was consumed, {@code false} otherwise
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        var area = getPaddedContentArea();
        int midX = area.topLeftX() + area.guiWidth() / 2;

        // Right page – content style links
        if (button == 0
                && mouseX >= midX
                && mouseY >= rightSubTopY
                && mouseY < rightSubBottomY
                && lastHoveredContentStyle != null) {

            if (hasShiftDown()
                    && ModConstants.RUN_FONT_CHATCOMMAND.equals(lastHoveredContentStyle.getFont())
                    && lastHoveredContentStyle.getInsertion() != null) {
                MinecraftClient.getInstance().setScreen(new ChatScreen(lastHoveredContentStyle.getInsertion()));
                return true;
            }

            if (ModConstants.RUN_FONT_KEYBIND.equals(lastHoveredContentStyle.getFont())) {
                MinecraftClient mc = MinecraftClient.getInstance();
                mc.setScreen(new KeybindsScreen(this, mc.options));
                return true;
            }

            ClickEvent clickEvent = lastHoveredContentStyle.getClickEvent();
            if (clickEvent != null && clickEvent.getAction() == ClickEvent.Action.OPEN_URL) {
                String url = clickEvent.getValue();
                if (!url.isBlank()) {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    mc.setScreen(new ConfirmLinkScreen(confirmed -> {
                        if (confirmed) Util.getOperatingSystem().open(url);
                        mc.setScreen(this);
                    }, url, false));
                    return true;
                }
            }
        }

        // Left page – buttons
        if (mouseX < midX) {
            if (viewState == ViewState.ENTRY_VIEW) {
                // Entry buttons (only in scissored viewport)
                if (mouseY >= leftListTopY && mouseY < leftBottomY) {
                    for (StyledButtonWidget btn : entryButtons) {
                        if (btn.mouseClicked(mouseX, mouseY, button)) return true;
                    }
                }
                // Back button (always visible below the list)
                if (backButton != null && backButton.mouseClicked(mouseX, mouseY, button)) return true;
            } else {
                // Page buttons
                if (mouseY >= leftListTopY && mouseY < leftBottomY) {
                    for (StyledButtonWidget btn : pageButtons) {
                        if (btn.mouseClicked(mouseX, mouseY, button)) return true;
                    }
                }
            }
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    /**
     * Handles mouse scroll-wheel events.
     *
     * <p>Scroll events are routed based on cursor position and {@link #viewState}:</p>
     * <ul>
     *   <li><b>Left of the screen mid-point</b> and within the left-column list:
     *       {@link #entryScrollbar} in {@link ViewState#ENTRY_VIEW}, or
     *       {@link #leftScrollbar} in {@link ViewState#PAGE_LIST}.</li>
     *   <li><b>Right of the screen mid-point</b> and within the right viewport:
     *       always {@link #contentScrollbar}.</li>
     * </ul>
     *
     * @param mouseX screen-space X of the cursor
     * @param mouseY screen-space Y of the cursor
     * @param amount scroll amount (positive = up / away from user)
     * @return {@code true} if the event was consumed, {@code false} otherwise
     */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        var area = getPaddedContentArea();
        int midX = area.topLeftX() + area.guiWidth() / 2;

        if (mouseX < midX) {
            if (mouseY >= leftListTopY && mouseY < leftBottomY) {
                return viewState == ViewState.ENTRY_VIEW
                        ? entryScrollbar.scroll(amount)
                        : leftScrollbar.scroll(amount);
            }
        } else {
            if (mouseY >= rightSubTopY && mouseY < rightSubBottomY) {
                return contentScrollbar.scroll(amount);
            }
        }

        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    /**
     * @return true - this screen can be searched
     */
    @Override
    protected boolean isSearchable() {
        return true;
    }

    /**
     * Returns the content padding for this screen.
     *
     * <p>The Guide Screen manages its own internal margins via {@link #LEFT_MARGINS}
     * and {@link #RIGHT_MARGINS}, so no additional global padding is needed.</p>
     *
     * @return {@code 0} – no extra padding around the content area
     */
    @Override
    public int getContentPadding() {
        return 0;
    }

    /**
     * Immutable record holding the four inner margin values (in pixels) for a column.
     *
     * @param left   space between the column's left boundary and its content
     * @param right  space between the column's right boundary and its content
     * @param top    space between the column's top boundary and its content
     * @param bottom space between the column's bottom boundary and its content
     */
    @SuppressWarnings("SameParameterValue")
    private record Margins(int left, int right, int top, int bottom) {
    }
}