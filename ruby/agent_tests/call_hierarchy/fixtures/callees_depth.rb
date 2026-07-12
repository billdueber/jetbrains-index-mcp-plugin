def deep_helper
  true
end

def helper
  deep_helper
end

def caller
  helper
  standalone
end

def standalone
  true
end