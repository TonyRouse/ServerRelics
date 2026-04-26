package com.serverrelics.relics.impl;

import com.serverrelics.ServerRelics;
import com.serverrelics.relics.Relic;

/**
 * A generic relic implementation for simple relics that don't need special code.
 *
 * All behavior is defined in config.yml:
 * - Display settings (name, material, lore)
 * - Effects to apply
 * - Restrictions
 * - BlueMap integration
 * - Broadcasts
 *
 * Use this for new relics that just need different config values.
 * For relics that need custom behavior, create a new class extending Relic.
 */
public class GenericRelic extends Relic {

    public GenericRelic(ServerRelics plugin, String id) {
        super(plugin, id);
    }

    // Generic relic uses all default behavior from Relic base class
    // Override methods here only if you need generic relics to have
    // shared custom behavior different from the base class
}
