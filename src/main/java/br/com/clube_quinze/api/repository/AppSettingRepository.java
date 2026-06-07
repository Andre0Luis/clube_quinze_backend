package br.com.clube_quinze.api.repository;

import br.com.clube_quinze.api.model.settings.AppSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppSettingRepository extends JpaRepository<AppSetting, String> {
}
