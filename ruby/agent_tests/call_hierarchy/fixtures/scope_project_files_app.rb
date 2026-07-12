require '/tmp/jbimcp_scope_library/lib/scope_library'

def call_library_target
  LibraryScope::Helper.new.call_target
end
