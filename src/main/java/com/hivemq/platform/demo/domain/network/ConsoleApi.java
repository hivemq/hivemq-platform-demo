package com.hivemq.platform.demo.domain.network;

import com.hivemq.platform.demo.domain.dto.UserConfigDto;
import io.reactivex.rxjava3.core.Single;
import retrofit2.http.GET;

public interface ConsoleApi {

    @GET("api/v3/user-config")
    Single<UserConfigDto> getUserConfig();
}
