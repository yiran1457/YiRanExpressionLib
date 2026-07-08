package net.yiran.expressionlib;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(YiRanExpressionLib.MODID)
public class YiRanExpressionLib {
    public static final String MODID = "yiranexpressionlib";
    public static final Logger LOGGER = LogUtils.getLogger();

    public YiRanExpressionLib(IEventBus modEventBus, ModContainer modContainer) {
    }

}

