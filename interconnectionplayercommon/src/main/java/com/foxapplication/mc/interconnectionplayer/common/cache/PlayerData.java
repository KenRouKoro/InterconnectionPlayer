package com.foxapplication.mc.interconnectionplayer.common.cache;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PlayerData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1145141919810L;

    private String UUID;
    private byte[] data;
}
