package net.evmodder.DropHeads;

import java.util.ArrayList;
import javax.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;
import me.arcaniax.hdb.api.HeadDatabaseAPI;
import net.evmodder.EvLib.extras.TextUtils;
import net.evmodder.EvLib.extras.TellrawUtils.Component;
import net.evmodder.EvLib.extras.TellrawUtils.TranslationComponent;

/** Internal-only API for DropHeads.
 * WARNING: Functions here are highly subject to change and should not be used by other plugins
 */
public final class InternalAPI extends HeadAPI{
	InternalAPI(NoteblockMode m, boolean crackedIronGolemHeads){super(m, crackedIronGolemHeads);}

	// Loads config.getString(key), replacing '${abc-xyz}' with config.getString('abc-xyz')
	/** <strong>DO NOT USE:</strong> This function will likely disappear in a future release
	 * @param key path in <a href="https://github.com/EvModder/DropHeads/blob/master/translations.yml">translations.yml"</a>
	 * @return the value at that path, or the value of <code>key</code> if not found
	 */
	public String loadTranslationStr(String key){return loadTranslationStr(key, key);}

	/** <strong>DO NOT USE:</strong> This function will likely disappear in a future release
	 * @param key path in <a href="https://github.com/EvModder/DropHeads/blob/master/translations.yml">translations.yml"</a>
	 * @param defaultValue a value for if the key is not found
	 * @return the value at that path, or the value of <code>defaultValue</code> if not found
	 */
	public String loadTranslationStr(String key, String defaultValue){
		if(!pl.getConfig().isString(key)) DropHeads.getPlugin().getLogger().severe("Undefined key in 'translations.yml': "+key);
		final String msg = TextUtils.translateAlternateColorCodes('&', pl.getConfig().getString(key, defaultValue));
		int i = msg.indexOf('$');
		if(i == -1) return msg;
		StringBuilder builder = new StringBuilder();
		builder.append(msg.substring(0, i));
		while(true){
			if(msg.charAt(++i) == '{'){
				final int subStart = i + 1;
				while(msg.charAt(++i) == '-' || msg.charAt(i) == '.' || (msg.charAt(i) >= 'a' && msg.charAt(i) <= 'z'));
				if(msg.charAt(i) == '}'){
					builder.append(loadTranslationStr(msg.substring(subStart, i)));
					++i;
				}
				else builder.append(msg.substring(subStart-2, i));
			}
			else builder.append('$');
			final int nextI = msg.indexOf('$', i);
			if(nextI == -1) break;
			builder.append(msg.substring(i, nextI));
			i = nextI;
		}
		builder.append(msg.substring(i));
		return builder.toString();
	}

	// Loads config.getString(key), replacing '${abc-xyz}' with % in the key and config.getString('abc-xyz') in withComps.
	/** <strong>DO NOT USE:</strong> This function will likely disappear in a future release
	 * @param key path in <a href="https://github.com/EvModder/DropHeads/blob/master/translations.yml">translations.yml"</a>
	 * @return the parsed value at that path, or the parsed value of <code>key</code> if not found
	 */
	public TranslationComponent loadTranslationComp(String key){return loadTranslationComp(key, key);}

	/** <strong>DO NOT USE:</strong> This function will likely disappear in a future release
	 * @param key path in <a href="https://github.com/EvModder/DropHeads/blob/master/translations.yml">translations.yml"</a>
	 * @param defaultValue a value for if the key is not found
	 * @return the parsed value at that path, or the parsed value of <code>defaultValue</code> if not found
	 */
	public TranslationComponent loadTranslationComp(String key, String defaultValue){
//		if(!translationsFile.isString(key)) pl.getLogger().severe("Undefined key in 'translations.yml': "+key);
		final String msg = TextUtils.translateAlternateColorCodes('&', pl.getConfig().getString(key, defaultValue));
		int i = msg.indexOf('$');//${sub}
		if(i == -1){
			return new TranslationComponent(msg);
		}
		if(i == 0 && msg.charAt(1) == '{' && msg.indexOf('}') == msg.length()-1){
			return loadTranslationComp(msg.substring(2, msg.length()-1));
		}
		ArrayList<Component> withComps = new ArrayList<>();
		StringBuilder builder = new StringBuilder();
		builder.append(msg.substring(0, i));
		while(true){
			if(msg.charAt(++i) == '{'){
				final int subStart = i + 1;
				while(msg.charAt(++i) == '-' || msg.charAt(i) == '.' || (msg.charAt(i) >= 'a' && msg.charAt(i) <= 'z'));
				if(msg.charAt(i) == '}'){
					//TODO: getCurrentColorAndFormatProperties(msg, i) -> put onto newly added with-comp
					withComps.add(loadTranslationComp(msg.substring(subStart, i)));
					builder.append("%s");
					++i;
				}
				else builder.append(msg.substring(subStart-2, i));
			}
			else builder.append('$');
			final int nextI = msg.indexOf('$', i);
			if(nextI == -1) break;
			builder.append(msg.substring(i, nextI));
			i = nextI;
		}
		builder.append(msg.substring(i));
		return new TranslationComponent(builder.toString(), withComps.toArray(new Component[0]));
	}

	/** <strong>DO NOT USE:</strong> This function is intended for internal use only
	 * @return an instance of the HDB API
	 */
	public HeadDatabaseAPI getHeadDatabaseAPI(){return hdbAPI;}

	/** <strong>DO NOT USE:</strong> This function is intended for internal use only
	 * @param head the item to check
	 * @return whether the head is from HDB
	 */
	public boolean isHeadDatabaseHead(ItemStack head){
		if(hdbAPI == null) return false;
		String id = hdbAPI.getItemID(head);
		return id != null && hdbAPI.isHead(id);
	}

	// Called by BlockClickListener and ItemDropListener
	/** <strong>DO NOT USE:</strong> This function will likely disappear in a future release
	 * @param textureKey the key (from <a href="https://github.com/EvModder/DropHeads/blob/master/head-textures.txt">head-textures.txt"</a>)
	 * @param customName the custom name of the entity, usually null for non-players
	 * @return localized name component
	 */
	public Component getFullHeadNameFromKey(@Nonnull String textureKey, @Nonnull String customName){return super.getFullHeadNameFromKey(textureKey, customName);}
}