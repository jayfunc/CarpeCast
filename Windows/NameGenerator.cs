using System;

namespace CarpeCast
{
    public static class NameGenerator
    {
        private static readonly string[] Adjectives = {
            "Clever", "Fast", "Brave", "Silent", "Mighty", "Quick", "Happy",
            "Bright", "Cool", "Calm", "Fierce", "Gentle", "Lucky", "Proud"
        };

        private static readonly string[] Nouns = {
            "Fox", "Tiger", "Bear", "Eagle", "Wolf", "Lion", "Hawk",
            "Owl", "Panda", "Shark", "Falcon", "Dolphin", "Panther", "Leopard"
        };

        public static string Generate()
        {
            var random = new Random();
            string adj = Adjectives[random.Next(Adjectives.Length)];
            string noun = Nouns[random.Next(Nouns.Length)];
            return $"{adj}-{noun}";
        }
    }
}
