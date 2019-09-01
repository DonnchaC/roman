package com.wire.bots.roman.DAO;

import com.wire.bots.roman.DAO.mappers.ProviderMapper;
import com.wire.bots.roman.model.Provider;
import org.skife.jdbi.v2.sqlobject.Bind;
import org.skife.jdbi.v2.sqlobject.SqlQuery;
import org.skife.jdbi.v2.sqlobject.SqlUpdate;
import org.skife.jdbi.v2.sqlobject.customizers.RegisterMapper;

import java.util.UUID;

public interface AdminsDAO {
    @SqlUpdate("INSERT INTO Admins (id, email, hash, password) " +
            "VALUES (:id, :email, :hash, :password)")
    int insert(@Bind("id") UUID id,
               @Bind("email") String email,
               @Bind("hash") String hash,
               @Bind("password") String password);

    @SqlUpdate("UPDATE Admins SET " +
            "url = :url," +
            "service_auth = :auth " +
            "WHERE id = :id")
    void add(@Bind("id") UUID id,
             @Bind("url") String url,
             @Bind("auth") String auth);

    @SqlQuery("SELECT * FROM Admins WHERE email = :email")
    @RegisterMapper(ProviderMapper.class)
    Provider get(@Bind("email") String email);

    @SqlQuery("SELECT * FROM Admins WHERE id = :id")
    @RegisterMapper(ProviderMapper.class)
    Provider get(@Bind("id") UUID id);

    @SqlQuery("SELECT url FROM Admins WHERE service_auth = :auth")
    String getUrl(@Bind("auth") String auth);
}