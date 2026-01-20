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

import com.hypixel.hytale.server.core.command.CommandSender;
import com.hypixel.hytale.server.core.universe.player.Player;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.HytaleServer;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.logging.Level;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.identity.Identity;
import net.kyori.adventure.platform.facet.FacetAudienceProvider;
import net.kyori.adventure.platform.facet.Knob;
import net.kyori.adventure.pointer.Pointered;
import net.kyori.adventure.text.flattener.ComponentFlattener;
import net.kyori.adventure.text.renderer.ComponentRenderer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

final class HytaleAudiencesImpl extends FacetAudienceProvider<CommandSender, HytaleAudience> implements HytaleAudiences {
  static {
    Knob.OUT = message -> HytaleServer.getInstance().getLogger().log(Level.INFO, message);
    Knob.ERR = (message, error) -> HytaleServer.getInstance().getLogger().log(Level.WARNING, message, error);
  }

  private static final Map<String, HytaleAudiences> INSTANCES = Collections.synchronizedMap(new HashMap<>(4));

  static @NotNull HytaleAudiences instanceFor(final @NotNull JavaPlugin plugin) {
    return builder(plugin).build();
  }

  static @NotNull Builder builder(final @NotNull JavaPlugin plugin) {
    return new Builder(plugin);
  }

  private final JavaPlugin plugin;

  HytaleAudiencesImpl(final JavaPlugin plugin, final @NotNull ComponentRenderer<Pointered> componentRenderer) {
    super(componentRenderer);
    this.plugin = requireNonNull(plugin, "plugin");

    final CommandSender console = HytaleServer.getInstance().getConsole();
    this.addViewer(console);

    for (final Player player : HytaleServer.getInstance().getPlayers()) {
      this.addViewer(player);
    }
  }

  @NotNull
  @Override
  public Audience sender(final @NotNull CommandSender sender) {
    if (sender instanceof Player) {
      return this.player((Player) sender);
    } else if (HytaleServer.getInstance().getConsole().equals(sender)) {
      return this.console();
    }
    return this.createAudience(Collections.singletonList(sender));
  }

  @NotNull
  @Override
  public Audience player(final @NotNull Player player) {
    return this.player(player.getUniqueId());
  }

  @Override
  protected @NotNull HytaleAudience createAudience(final @NotNull Collection<CommandSender> viewers) {
    return new HytaleAudience(this, viewers);
  }

  @Override
  public @NotNull ComponentFlattener flattener() {
    return HytaleFacet.FLATTENER;
  }

  @Override
  public void close() {
    HytaleAudiencesImpl.INSTANCES.remove(this.plugin.getName());
    super.close();
  }

  static final class Builder implements HytaleAudiences.Builder {
    private final @NotNull JavaPlugin plugin;
    private ComponentRenderer<Pointered> componentRenderer;

    Builder(final @NotNull JavaPlugin plugin) {
      this.plugin = requireNonNull(plugin, "plugin");
      this.componentRenderer(ptr -> ptr.getOrDefault(Identity.LOCALE, DEFAULT_LOCALE), GlobalTranslator.renderer());
    }

    @Override
    public @NotNull Builder componentRenderer(final @NotNull ComponentRenderer<Pointered> componentRenderer) {
      this.componentRenderer = requireNonNull(componentRenderer, "component renderer");
      return this;
    }

    @Override
    public HytaleAudiences.@NotNull Builder partition(final @NotNull Function<Pointered, ?> partitionFunction) {
      requireNonNull(partitionFunction, "partitionFunction");
      return this;
    }

    @Override
    public @NotNull HytaleAudiences build() {
      return INSTANCES.computeIfAbsent(this.plugin.getName(), name -> new HytaleAudiencesImpl(this.plugin, this.componentRenderer));
    }
  }
}
