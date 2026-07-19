package main

import (
	"fmt"
	"log"
	"os"
	"time"

	"github.com/gofiber/fiber/v3"
	"github.com/samuelh2005/ServerAnnounce/backend/config"
)

func main() {
	// Load the configuration file from the CLI argument or ENV variable.
	configPath := os.Getenv("CONFIG_PATH")
	if configPath == "" {
		if len(os.Args) < 2 {
			log.Fatal("Please provide a configuration file path as an argument or set the CONFIG_PATH environment variable.")
		}
		configPath = os.Args[1]
	}

	if err := config.Load(configPath); err != nil {
		log.Fatal(err)
	}

	if err := config.Watch(configPath); err != nil {
		log.Fatal(err)
	}

	// Initialize a new Fiber app
	app := fiber.New()

	// Define a route for the GET method on the root path '/'
	app.Get("/", func(c fiber.Ctx) error {
		cfg := config.Get()
		return c.JSON(fiber.Map{
			"status":  "ok",
			"version": "1.0.0",
			"port":    cfg.Server.Port,
		})
	})

	// Define a route for the GET method on the path '/v1/servers'
	app.Get("/v1/servers", func(c fiber.Ctx) error {
		// Return a JSON response with a list of servers. Each server has a name, an address/port a group and a default expiry time in seconds.
		cfg := config.Get()

		// expiry is calculated from the current time + the default server expiry time in seconds. We will return this as a unix timestamp in seconds.
		// expiry allows the proxy to refresh if config changes or we add dynamic servers later.
		expiry := time.Now().Add(time.Duration(cfg.Server.DefaultServerExpiry) * time.Second).Unix()

		// We need to rebuild the response rather then just pulling from the config because we need to add the expiry time to each server.
		response := make([]fiber.Map, len(cfg.Servers))
		for i, server := range cfg.Servers {
			// If the server has a group, we will include it in the response. Otherwise, we will not include it.
			if server.Group != "" {
				response[i] = fiber.Map{
					"name":    server.Name,
					"address": server.Address,
					"group":   server.Group,
					"expiry":  expiry,
				}
			} else {
				response[i] = fiber.Map{
					"name":    server.Name,
					"address": server.Address,
					"expiry":  expiry,
				}
			}
		}
		return c.JSON(fiber.Map{
			"servers": response,
		})
	})

	// Start the server on the port specified in the configuration file
	cfg := config.Get()
	log.Fatal(app.Listen(fmt.Sprintf(":%d", cfg.Server.Port)))
}
