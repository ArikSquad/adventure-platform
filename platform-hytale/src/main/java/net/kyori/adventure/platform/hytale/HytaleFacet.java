/*
 * This file is part of adventure-platform, licensed under the MIT License.
 *
 * Copyright (c) 2018-2020 KyoriPowered
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package net.kyori.adventure.platform.hytale;

import com.hypixel.hytale.api.CommandSender;
import com.hypixel.hytale.api.Player;
import com.hypixel.hytale.api.Server;
import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import net.kyori.adventure.audience.MessageType;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.permission.PermissionChecker;
import net.kyori.adventure.platform.facet.Facet;
import net.kyori.adventure.platform.facet.FacetBase;
import net.kyori.adventure.platform.facet.FacetComponentFlattener;
import net.kyori.adventure.platform.facet.FacetPointers;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.util.TriState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.kyori.adventure.platform.facet.Knob.logUnsupported;

class HytaleFacet<V extends CommandSender> extends FacetBase<V> {
  static final ComponentFlattener FLATTENER = FacetComponentFlattener.get(Server.getInstance(), null);
  static final GsonComponentSerializer MODERN = GsonComponentSerializer.gson();
  static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.builder().flattener(FLATTENER).build();

  protected HytaleFacet(final @Nullable Class<? extends V> viewerClass) {
    super(viewerClass);
  }

  static class ChatConsole extends HytaleFacet<CommandSender> implements Facet.Chat<CommandSender, String> {
    protected ChatConsole() {
      super(CommandSender.class);
    }

    @Override
    public boolean isApplicable(final @NotNull CommandSender viewer) {
      return super.isApplicable(viewer) && !(viewer instanceof Player);
    }

    @Override
    public String createMessage(final @NotNull CommandSender viewer, final @NotNull Component message) {
      return LEGACY.serialize(message);
    }

    @Override
    public void sendMessage(final @NotNull CommandSender viewer, final @NotNull Identity source, final @NotNull String message, final @NotNull Object type) {
      viewer.sendMessage(message);
    }
  }

  static class Message extends HytaleFacet<Player> implements Facet.Message<Player, String> {
    protected Message() {
      super(Player.class);
    }

    @Override
    public String createMessage(final @NotNull Player viewer, final @NotNull Component message) {
      return MODERN.serialize(message);
    }
  }

  static class ChatPlayer extends Message implements Facet.Chat<Player, String> {
    @Override
    public void sendMessage(final @NotNull Player viewer, final @NotNull Identity source, final @NotNull String message, final @NotNull Object type) {
      viewer.sendMessage(message);
    }
  }

  static class ActionBar extends Message implements Facet.ActionBar<Player, String> {
    @Override
    public void sendMessage(final @NotNull Player viewer, final @NotNull String message) {
      viewer.sendActionBar(message);
    }
  }

  static class Title extends Message implements Facet.Title<Player, String, TitleData, TitleData> {
    @Override
    public @NotNull TitleData createTitleCollection() {
      return new TitleData();
    }

    @Override
    public void contributeTitle(final @NotNull TitleData coll, final @NotNull String title) {
      coll.title = title;
    }

    @Override
    public void contributeSubtitle(final @NotNull TitleData coll, final @NotNull String subtitle) {
      coll.subtitle = subtitle;
    }

    @Override
    public void contributeTimes(final @NotNull TitleData coll, final int inTicks, final int stayTicks, final int outTicks) {
      if (inTicks > -1) coll.fadeIn = inTicks;
      if (stayTicks > -1) coll.stay = stayTicks;
      if (outTicks > -1) coll.fadeOut = outTicks;
    }

    @Nullable
    @Override
    public TitleData completeTitle(final @NotNull TitleData coll) {
      return coll;
    }

    @Override
    public void showTitle(final @NotNull Player viewer, final @NotNull TitleData title) {
      viewer.sendTitle(title.title, title.subtitle, title.fadeIn, title.stay, title.fadeOut);
    }

    @Override
    public void clearTitle(final @NotNull Player viewer) {
      viewer.clearTitle();
    }

    @Override
    public void resetTitle(final @NotNull Player viewer) {
      viewer.resetTitle();
    }

    static class TitleData {
      String title = "";
      String subtitle = "";
      int fadeIn = 10;
      int stay = 70;
      int fadeOut = 20;
    }
  }

  static class BossBar extends Message implements BossBarPacket<Player> {
    private final Set<Player> viewers;
    private final UUID barId;
    private volatile boolean initialized = false;

    protected BossBar(final @NotNull Collection<Player> viewers) {
      super();
      this.viewers = new CopyOnWriteArraySet<>(viewers);
      this.barId = UUID.randomUUID();
    }

    static class Builder extends HytaleFacet<Player> implements Facet.BossBar.Builder<Player, net.kyori.adventure.platform.hytale.HytaleFacet.BossBar> {
      protected Builder() {
        super(Player.class);
      }

      @Override
      public net.kyori.adventure.platform.hytale.HytaleFacet.@NotNull BossBar createBossBar(final @NotNull Collection<Player> viewers) {
        return new net.kyori.adventure.platform.hytale.HytaleFacet.BossBar(viewers);
      }
    }

    @Override
    public void bossBarInitialized(final net.kyori.adventure.bossbar.@NotNull BossBar bar) {
      BossBarPacket.super.bossBarInitialized(bar);
      this.initialized = true;
      for (final Player viewer : this.viewers) {
        this.showBossBar(viewer, bar);
      }
    }

    @Override
    public void bossBarNameChanged(final net.kyori.adventure.bossbar.@NotNull BossBar bar, final @NotNull Component oldName, final @NotNull Component newName) {
      for (final Player viewer : this.viewers) {
        this.updateBossBar(viewer, bar);
      }
    }

    @Override
    public void bossBarProgressChanged(final net.kyori.adventure.bossbar.@NotNull BossBar bar, final float oldPercent, final float newPercent) {
      for (final Player viewer : this.viewers) {
        this.updateBossBar(viewer, bar);
      }
    }

    @Override
    public void bossBarColorChanged(final net.kyori.adventure.bossbar.@NotNull BossBar bar, final net.kyori.adventure.bossbar.BossBar.@NotNull Color oldColor, final net.kyori.adventure.bossbar.BossBar.@NotNull Color newColor) {
      for (final Player viewer : this.viewers) {
        this.updateBossBar(viewer, bar);
      }
    }

    @Override
    public void bossBarOverlayChanged(final net.kyori.adventure.bossbar.@NotNull BossBar bar, final net.kyori.adventure.bossbar.BossBar.@NotNull Overlay oldOverlay, final net.kyori.adventure.bossbar.BossBar.@NotNull Overlay newOverlay) {
      for (final Player viewer : this.viewers) {
        this.updateBossBar(viewer, bar);
      }
    }

    @Override
    public void bossBarFlagsChanged(final net.kyori.adventure.bossbar.@NotNull BossBar bar, final @NotNull Set<net.kyori.adventure.bossbar.BossBar.Flag> flagsAdded, final @NotNull Set<net.kyori.adventure.bossbar.BossBar.Flag> flagsRemoved) {
      for (final Player viewer : this.viewers) {
        this.updateBossBar(viewer, bar);
      }
    }

    @Override
    public void addViewer(final @NotNull Player viewer) {
      this.viewers.add(viewer);
    }

    @Override
    public void removeViewer(final @NotNull Player viewer) {
      this.viewers.remove(viewer);
      viewer.hideBossBar(this.barId);
    }

    @Override
    public boolean isEmpty() {
      return !this.initialized || this.viewers.isEmpty();
    }

    @Override
    public void close() {
      for (final Player viewer : this.viewers) {
        viewer.hideBossBar(this.barId);
      }
      this.viewers.clear();
    }

    private void showBossBar(final Player viewer, final net.kyori.adventure.bossbar.BossBar bar) {
      viewer.showBossBar(this.barId, this.createMessage(viewer, bar.name()), bar.progress(), this.createColor(bar.color()), this.createOverlay(bar.overlay()));
    }

    private void updateBossBar(final Player viewer, final net.kyori.adventure.bossbar.BossBar bar) {
      viewer.updateBossBar(this.barId, this.createMessage(viewer, bar.name()), bar.progress(), this.createColor(bar.color()), this.createOverlay(bar.overlay()));
    }
  }

  static final class TabList extends Message implements Facet.TabList<Player, String> {
    @Override
    public void send(final Player viewer, final @Nullable String header, final @Nullable String footer) {
      viewer.setTabHeader(header == null ? "" : header, footer == null ? "" : footer);
    }
  }

  static final class CommandSenderPointers extends HytaleFacet<CommandSender> implements Facet.Pointers<CommandSender> {
    CommandSenderPointers() {
      super(CommandSender.class);
    }

    @Override
    public void contributePointers(final CommandSender viewer, final net.kyori.adventure.pointer.Pointers.Builder builder) {
      builder.withDynamic(Identity.NAME, viewer::getName);
      builder.withStatic(PermissionChecker.POINTER, perm -> viewer.hasPermission(perm) ? TriState.TRUE : TriState.FALSE);
      if (!(viewer instanceof Player)) {
        builder.withStatic(FacetPointers.TYPE, viewer == Server.getInstance().getConsole() ? FacetPointers.Type.CONSOLE : FacetPointers.Type.OTHER);
      }
    }
  }

  static final class PlayerPointers extends HytaleFacet<Player> implements Facet.Pointers<Player> {
    PlayerPointers() {
      super(Player.class);
    }

    @Override
    public void contributePointers(final Player viewer, final net.kyori.adventure.pointer.Pointers.Builder builder) {
      builder.withDynamic(Identity.UUID, viewer::getUniqueId);
      builder.withDynamic(Identity.LOCALE, viewer::getLocale);
      builder.withStatic(FacetPointers.TYPE, FacetPointers.Type.PLAYER);
    }
  }
}
