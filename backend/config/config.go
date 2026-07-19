package config

import (
	"log"
	"sync"

	"github.com/fsnotify/fsnotify"
	"github.com/knadh/koanf/parsers/yaml"
	"github.com/knadh/koanf/providers/file"
	"github.com/knadh/koanf/v2"
)

type Server struct {
	Name    string `koanf:"name"`
	Address string `koanf:"address"`
	Group   string `koanf:"group,omitempty"`
}

type Config struct {
	Server struct {
		Port                int `koanf:"port"`
		DefaultServerExpiry int `koanf:"default_server_expiry"`
	} `koanf:"server" json:"server"`

	Servers []Server `koanf:"servers"`
}

var (
	k  = koanf.New(".")
	mu sync.RWMutex

	Cfg Config
)

func Load(path string) error {
	if err := k.Load(file.Provider(path), yaml.Parser()); err != nil {
		return err
	}

	var cfg Config
	if err := k.Unmarshal("", &cfg); err != nil {
		return err
	}

	mu.Lock()
	Cfg = cfg
	mu.Unlock()

	log.Println("Configuration loaded")

	return nil
}

func Watch(path string) error {
	return file.Provider(path).Watch(func(event interface{}, err error) {
		if err != nil {
			log.Println("watch error:", err)
			return
		}

		if _, ok := event.(fsnotify.Event); ok {
			log.Println("Reloading configuration...")

			if err := Load(path); err != nil {
				log.Println("reload failed:", err)
			}
		}
	})
}

func Get() Config {
	mu.RLock()
	defer mu.RUnlock()
	return Cfg
}
