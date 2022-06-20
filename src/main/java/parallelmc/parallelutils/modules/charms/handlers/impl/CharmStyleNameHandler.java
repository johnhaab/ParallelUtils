package parallelmc.parallelutils.modules.charms.handlers.impl;

import io.papermc.paper.text.PaperComponents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import parallelmc.parallelutils.Parallelutils;
import parallelmc.parallelutils.modules.charms.data.CharmOptions;
import parallelmc.parallelutils.modules.charms.data.IEffectSettings;
import parallelmc.parallelutils.modules.charms.handlers.HandlerType;
import parallelmc.parallelutils.modules.charms.handlers.ICharmApplyHandler;
import parallelmc.parallelutils.modules.charms.helper.EncapsulatedType;
import parallelmc.parallelutils.modules.charms.helper.Types;

import java.util.HashMap;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

public class CharmStyleNameHandler extends ICharmApplyHandler {

	@Override
	public void apply(Player player, @NotNull ItemStack item, @NotNull CharmOptions options) {

		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return;
		}

		HashMap<HandlerType, IEffectSettings> effects = options.getEffects();

		IEffectSettings settings = effects.get(HandlerType.STYLE_NAME);

		if (settings == null) {
			return;
		}

		HashMap<String, EncapsulatedType> settingMap = settings.getSettings();

		EncapsulatedType type = settingMap.get("style");

		if (type == null || type.getType() != Types.STRING) {
			return;
		}

		String val = (String) type.getVal();

		Component currName = meta.displayName();

		if (currName == null) {
			//currName = GlobalTranslator.render(Component.translatable(item), Locale.ENGLISH); // This almost works...
			String name = item.getI18NDisplayName(); // I tried so hard to not use deprecated stuff. Formats + translatables are hard...
			currName = Component.text(Objects.requireNonNullElseGet(name, item::translationKey));
			val = "<italic:false>" + val;
		}

		TagResolver placeholders = TagResolver.resolver(Placeholder.component("name", currName));

		Component result = MiniMessage.builder().build().deserialize(val, placeholders);

		meta.displayName(result);

		meta.displayName();

		item.setItemMeta(meta);
	}

	@Override
	public void remove(Player player, ItemStack item, CharmOptions options) {
		ItemMeta meta = item.getItemMeta();

		if (meta == null) {
			return;
		}

		meta.displayName(null);

		item.setItemMeta(meta);
	}

	@Override
	public HandlerType getHandlerType() {
		return HandlerType.STYLE_NAME;
	}
}
