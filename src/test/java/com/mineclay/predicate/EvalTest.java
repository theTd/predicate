package com.mineclay.predicate;

import groovy.lang.Binding;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.logging.Logger;

public class EvalTest {

    @Test
    public void run() throws Exception {
        Service service = new Service(Mockito.mock(Logger.class), new Binding());
        service.addMethod(NameMethod.class);

        Player player = Mockito.mock(Player.class);
        Mockito.when(player.getName()).thenReturn("theTd");

        assert service.test(player, "'普通'=='普通'");
        assert !service.test(player, "2<1");
        assert service.test(player, "name() == 'theTd'");
        assert service.test(player, "'普通'=='普通' && 2 > 1 && name() == 'theTd'");
        assert service.test(player, "('普通'=='普通' || 2 < 1) && name() == 'theTd'");
    }
}
